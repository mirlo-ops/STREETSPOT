const BUCKET = "shared-spot-photos";
const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
const MAX_UID_LENGTH = 128;
const MAX_SPOT_ID_LENGTH = 128;
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
};

class HttpError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

type FirebaseClaims = {
  sub: string;
  iss: string;
  aud: string;
  exp: number;
  iat: number;
  auth_time: number;
  email_verified?: boolean;
};

type FirebaseJwk = JsonWebKey & { kid: string };

let cachedJwks: { keys: FirebaseJwk[]; expiresAt: number } | undefined;

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    requirePublishableKey(request.headers.get("apikey"));
    const projectId = requiredEnv("FIREBASE_PROJECT_ID");
    const firebaseToken = bearerToken(request.headers.get("authorization"));
    const claims = await verifyFirebaseIdToken(firebaseToken, projectId);
    const supabaseUrl = requiredEnv("SUPABASE_URL").replace(/\/$/, "");
    const serviceKey = serviceKeyFromEnv();

    const url = new URL(request.url);
    const ownerUid = validateUid(url.searchParams.get("ownerUid"));
    const spotIdValue = url.searchParams.get("spotId");
    const spotId = spotIdValue == null ? null : validateSpotId(spotIdValue);

    if (request.method === "POST") {
      if (claims.sub !== ownerUid) throw new HttpError(403, "Owner access required");
      if (claims.email_verified !== true) throw new HttpError(403, "Verified email required");
      if (!spotId) throw new HttpError(400, "spotId is required");
      const profile = await readFirestoreDocument(projectId, `profiles/${ownerUid}`, firebaseToken);
      if (profile.fields?.sharePhotos?.booleanValue !== true) {
        throw new HttpError(403, "Photo sharing is disabled");
      }

      const bytes = await readBodyWithinLimit(request, MAX_PHOTO_BYTES);
      if (!isJpeg(bytes)) throw new HttpError(415, "Only JPEG photos are accepted");
      const safeBytes = stripExifSegments(bytes);
      const path = photoPath(ownerUid, spotId);
      const response = await fetch(storageObjectUrl(supabaseUrl, path), {
        method: "POST",
        headers: serviceHeaders(serviceKey, { "Content-Type": "image/jpeg", "x-upsert": "true" }),
        body: safeBytes,
      });
      if (!response.ok) throw new HttpError(502, "Photo upload failed");
      return new Response(null, { status: 204, headers: corsHeaders });
    }

    if (request.method === "DELETE") {
      if (claims.sub !== ownerUid) throw new HttpError(403, "Owner access required");
      if (spotId) {
        const response = await deleteStorageObjects(supabaseUrl, serviceKey, [photoPath(ownerUid, spotId)]);
        if (!response.ok && response.status !== 404) throw new HttpError(502, "Photo removal failed");
      } else {
        await deleteAllStoragePhotos(supabaseUrl, serviceKey, ownerUid);
      }
      return new Response(null, { status: 204, headers: corsHeaders });
    }

    if (request.method === "GET") {
      if (!spotId) throw new HttpError(400, "spotId is required");
      if (claims.sub !== ownerUid) {
        // Firestore Rules are the source of truth for opt-in, accepted friendship,
        // and block state. A denied or missing metadata document denies the photo.
        await readFirestoreDocument(
          projectId,
          `profiles/${ownerUid}/sharedSpots/${spotId}`,
          firebaseToken,
        );
      }
      const response = await fetch(storageObjectUrl(supabaseUrl, photoPath(ownerUid, spotId), true), {
        headers: serviceHeaders(serviceKey),
      });
      if (!response.ok || !response.body) throw new HttpError(404, "Photo not found");
      return new Response(response.body, {
        status: 200,
        headers: {
          ...corsHeaders,
          "Content-Type": "image/jpeg",
          "Cache-Control": "private, no-store, max-age=0",
          "X-Content-Type-Options": "nosniff",
        },
      });
    }

    throw new HttpError(405, "Method not allowed");
  } catch (error) {
    const status = error instanceof HttpError ? error.status : 500;
    if (status >= 500) console.error("shared-photos request failed", error);
    return Response.json(
      { error: error instanceof HttpError && status < 500 ? error.message : "Photo request failed" },
      { status, headers: { ...corsHeaders, "Content-Type": "application/json" } },
    );
  }
});

function requiredEnv(name: string): string {
  const value = Deno.env.get(name)?.trim();
  if (!value) throw new HttpError(500, `Missing ${name} configuration`);
  return value;
}

function serviceKeyFromEnv(): string {
  const keysJson = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (keysJson) {
    try {
      const keys = JSON.parse(keysJson) as Record<string, string>;
      const defaultKey = keys.default ?? Object.values(keys)[0];
      if (defaultKey) return defaultKey;
    } catch {
      throw new HttpError(500, "Invalid Supabase secret key configuration");
    }
  }
  return requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
}

function requirePublishableKey(value: string | null): void {
  if (!value) throw new HttpError(401, "Missing API key");
  const configuredKeys = new Set<string>();
  const keysJson = Deno.env.get("SUPABASE_PUBLISHABLE_KEYS");
  if (keysJson) {
    try {
      Object.values(JSON.parse(keysJson) as Record<string, string>).forEach((key) => configuredKeys.add(key));
    } catch {
      throw new HttpError(500, "Invalid Supabase publishable key configuration");
    }
  }
  const legacyKey = Deno.env.get("SUPABASE_ANON_KEY");
  if (legacyKey) configuredKeys.add(legacyKey);
  if (!configuredKeys.has(value)) throw new HttpError(401, "Invalid API key");
}

function bearerToken(value: string | null): string {
  const match = value?.match(/^Bearer\s+(.+)$/i);
  if (!match) throw new HttpError(401, "Firebase ID token required");
  return match[1];
}

function validateUid(value: string | null): string {
  if (!value || value.length > MAX_UID_LENGTH || !/^[A-Za-z0-9_-]+$/.test(value)) {
    throw new HttpError(400, "Invalid ownerUid");
  }
  return value;
}

function validateSpotId(value: string): string {
  if (!value || value.length > MAX_SPOT_ID_LENGTH || !/^[A-Za-z0-9_-]+$/.test(value)) {
    throw new HttpError(400, "Invalid spotId");
  }
  return value;
}

async function verifyFirebaseIdToken(token: string, projectId: string): Promise<FirebaseClaims> {
  const parts = token.split(".");
  if (parts.length !== 3) throw new HttpError(401, "Invalid Firebase ID token");

  let header: { alg?: string; kid?: string };
  let claims: FirebaseClaims;
  try {
    header = JSON.parse(decodeBase64Url(parts[0])) as { alg?: string; kid?: string };
    claims = JSON.parse(decodeBase64Url(parts[1])) as FirebaseClaims;
  } catch {
    throw new HttpError(401, "Invalid Firebase ID token");
  }
  if (header.alg !== "RS256" || !header.kid) throw new HttpError(401, "Invalid Firebase token signature");

  const jwk = await findFirebaseJwk(header.kid);
  if (!jwk) throw new HttpError(401, "Unknown Firebase signing key");
  const key = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"],
  );
  const signatureValid = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    key,
    decodeBase64UrlBytes(parts[2]),
    new TextEncoder().encode(`${parts[0]}.${parts[1]}`),
  );
  if (!signatureValid) throw new HttpError(401, "Invalid Firebase token signature");

  const now = Math.floor(Date.now() / 1000);
  if (
    claims.iss !== `https://securetoken.google.com/${projectId}` ||
    claims.aud !== projectId ||
    !claims.sub ||
    claims.sub.length > MAX_UID_LENGTH ||
    !Number.isInteger(claims.exp) || claims.exp <= now ||
    !Number.isInteger(claims.iat) || claims.iat > now + 60 ||
    !Number.isInteger(claims.auth_time) || claims.auth_time > now + 60
  ) {
    throw new HttpError(401, "Expired or invalid Firebase ID token");
  }
  return claims;
}

async function findFirebaseJwk(kid: string): Promise<FirebaseJwk | undefined> {
  const now = Date.now();
  if (!cachedJwks || cachedJwks.expiresAt <= now) {
    cachedJwks = await fetchFirebaseJwks();
  }
  let jwk = cachedJwks.keys.find((item) => item.kid === kid);
  if (!jwk) {
    cachedJwks = await fetchFirebaseJwks();
    jwk = cachedJwks.keys.find((item) => item.kid === kid);
  }
  return jwk;
}

async function fetchFirebaseJwks(): Promise<{ keys: FirebaseJwk[]; expiresAt: number }> {
  const response = await fetch("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com");
  if (!response.ok) throw new HttpError(503, "Firebase signing keys are unavailable");
  const body = await response.json() as { keys?: FirebaseJwk[] };
  const maxAge = Number(response.headers.get("Cache-Control")?.match(/max-age=(\d+)/i)?.[1] ?? 3600);
  return { keys: body.keys ?? [], expiresAt: Date.now() + maxAge * 1000 };
}

function decodeBase64Url(value: string): string {
  return new TextDecoder().decode(decodeBase64UrlBytes(value));
}

function decodeBase64UrlBytes(value: string): Uint8Array {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  return Uint8Array.from(atob(base64), (character) => character.charCodeAt(0));
}

async function readFirestoreDocument(projectId: string, documentPath: string, token: string): Promise<any> {
  const encodedPath = documentPath.split("/").map(encodeURIComponent).join("/");
  const response = await fetch(
    `https://firestore.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/databases/(default)/documents/${encodedPath}`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  if (!response.ok) throw new HttpError(404, "Photo is unavailable");
  return await response.json();
}

async function readBodyWithinLimit(request: Request, limit: number): Promise<Uint8Array> {
  if (!request.body) throw new HttpError(400, "Photo body is required");
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > limit) {
      await reader.cancel();
      throw new HttpError(413, "Photo exceeds the 5 MB limit");
    }
    chunks.push(value);
  }
  if (size === 0) throw new HttpError(400, "Photo body is empty");
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return bytes;
}

function isJpeg(bytes: Uint8Array): boolean {
  return bytes.length >= 4 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff &&
    bytes[bytes.length - 2] === 0xff && bytes[bytes.length - 1] === 0xd9;
}

function stripExifSegments(bytes: Uint8Array): Uint8Array {
  const chunks: Uint8Array[] = [bytes.subarray(0, 2)];
  let offset = 2;
  while (offset < bytes.length) {
    const markerStart = offset;
    if (bytes[offset] !== 0xff) throw new HttpError(415, "Malformed JPEG file");
    while (offset < bytes.length && bytes[offset] === 0xff) offset++;
    if (offset >= bytes.length) throw new HttpError(415, "Malformed JPEG file");
    const marker = bytes[offset++];
    if (marker === 0xd9) {
      chunks.push(bytes.subarray(markerStart, offset));
      break;
    }
    if (marker === 0xda) {
      chunks.push(bytes.subarray(markerStart));
      break;
    }
    if (marker === 0x01 || (marker >= 0xd0 && marker <= 0xd7)) {
      chunks.push(bytes.subarray(markerStart, offset));
      continue;
    }
    if (offset + 2 > bytes.length) throw new HttpError(415, "Malformed JPEG segment");
    const segmentLength = (bytes[offset] << 8) | bytes[offset + 1];
    const segmentEnd = offset + segmentLength;
    if (segmentLength < 2 || segmentEnd > bytes.length) throw new HttpError(415, "Malformed JPEG segment");
    // JPEG APP1 segments carry EXIF and XMP metadata; remove them before cloud upload.
    if (marker !== 0xe1) chunks.push(bytes.subarray(markerStart, segmentEnd));
    offset = segmentEnd;
  }

  const length = chunks.reduce((total, chunk) => total + chunk.byteLength, 0);
  const result = new Uint8Array(length);
  let outputOffset = 0;
  for (const chunk of chunks) {
    result.set(chunk, outputOffset);
    outputOffset += chunk.byteLength;
  }
  return result;
}

function photoPath(ownerUid: string, spotId: string): string {
  return `users/${ownerUid}/sharedSpots/${spotId}.jpg`;
}

function storageObjectUrl(baseUrl: string, objectPath: string, authenticated = false): string {
  const encodedPath = objectPath.split("/").map(encodeURIComponent).join("/");
  const visibilityPath = authenticated ? "authenticated/" : "";
  return `${baseUrl}/storage/v1/object/${visibilityPath}${BUCKET}/${encodedPath}`;
}

function serviceHeaders(key: string, extra: Record<string, string> = {}): HeadersInit {
  return { Authorization: `Bearer ${key}`, apikey: key, ...extra };
}

async function deleteStorageObjects(baseUrl: string, key: string, paths: string[]): Promise<Response> {
  return await fetch(`${baseUrl}/storage/v1/object/${BUCKET}`, {
    method: "DELETE",
    headers: serviceHeaders(key, { "Content-Type": "application/json" }),
    body: JSON.stringify({ prefixes: paths }),
  });
}

async function deleteAllStoragePhotos(baseUrl: string, key: string, ownerUid: string): Promise<void> {
  const prefix = `users/${ownerUid}/sharedSpots`;
  while (true) {
    const response = await fetch(`${baseUrl}/storage/v1/object/list/${BUCKET}`, {
      method: "POST",
      headers: serviceHeaders(key, { "Content-Type": "application/json" }),
      body: JSON.stringify({ prefix, limit: 100, offset: 0, sortBy: { column: "name", order: "asc" } }),
    });
    if (!response.ok) throw new HttpError(502, "Photo list could not be loaded");
    const items = await response.json() as Array<{ name?: string; id?: string | null }>;
    const paths = items
      .filter((item) => item.name && item.id)
      .map((item) => `${prefix}/${item.name}`);
    if (paths.length === 0) return;
    const deleted = await deleteStorageObjects(baseUrl, key, paths);
    if (!deleted.ok) throw new HttpError(502, "Photo cleanup failed");
  }
}
