import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { after, before, beforeEach, test } from "node:test";
import { deleteApp, initializeApp } from "firebase/app";
import {
  applyActionCode,
  connectAuthEmulator,
  confirmPasswordReset,
  createUserWithEmailAndPassword,
  deleteUser,
  getAuth,
  reload,
  sendEmailVerification,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signOut,
} from "firebase/auth";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  Timestamp,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  writeBatch,
} from "firebase/firestore";
const projectId = "demo-streetspot";
const friendCode = "ABCD2345EFGH";
let environment;

async function readActionCode(email, operation) {
  const response = await fetch(
    `http://127.0.0.1:9099/emulator/v1/projects/${projectId}/oobCodes`,
    { signal: AbortSignal.timeout(5000) },
  );
  assert.equal(response.ok, true, "Auth emulator should expose its local action codes");
  const { oobCodes = [] } = await response.json();
  const action = oobCodes.find((item) => item.email === email && item.requestType === operation);
  assert.ok(action?.oobCode, `missing ${operation} action code for ${email}`);
  return action.oobCode;
}

before(async () => {
  environment = await initializeTestEnvironment({
    projectId,
    firestore: { rules: await readFile("../../firestore.rules", "utf8") },
  });
});

after(async () => {
  await environment?.cleanup();
});

beforeEach(async () => {
  await environment.clearFirestore();
});

test("Auth emulator supports verification, sign-in, password reset, and account deletion", async () => {
  const app = initializeApp({
    apiKey: "demo-api-key-for-streetspot-tests",
    authDomain: `${projectId}.firebaseapp.com`,
    projectId,
  }, `auth-flow-${Date.now()}`);
  const auth = getAuth(app);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  const email = `stage11-${Date.now()}@example.test`;
  let user;

  try {
    ({ user } = await createUserWithEmailAndPassword(auth, email, "TestPassword-123!"));
    assert.equal(user.emailVerified, false);
    await sendEmailVerification(user);
    await applyActionCode(auth, await readActionCode(email, "VERIFY_EMAIL"));
    await reload(auth.currentUser);
    assert.equal(auth.currentUser.emailVerified, true);

    await signOut(auth);
    ({ user } = await signInWithEmailAndPassword(auth, email, "TestPassword-123!"));
    await sendPasswordResetEmail(auth, email);
    await confirmPasswordReset(auth, await readActionCode(email, "PASSWORD_RESET"), "UpdatedPassword-456!");
    await signOut(auth);
    ({ user } = await signInWithEmailAndPassword(auth, email, "UpdatedPassword-456!"));
    await deleteUser(user);
    user = null;
    await assert.rejects(signInWithEmailAndPassword(auth, email, "UpdatedPassword-456!"));
  } finally {
    if (auth.currentUser) await deleteUser(auth.currentUser).catch(() => {});
    await signOut(auth).catch(() => {});
    await deleteApp(app);
  }
});

function signedIn(uid, emailVerified = true) {
  return environment.authenticatedContext(uid, { email_verified: emailVerified });
}

function makeProfile(displayName, overrides = {}) {
  return {
    displayName,
    shareLevelXp: false,
    shareAchievements: false,
    shareModels: false,
    sharePhotos: false,
    acceptFriendRequests: false,
    updatedAt: Timestamp.now(),
    ...overrides,
  };
}

async function seedFriendPair({
  owner = "alice",
  friend = "bob",
  sharePhotos = false,
  acceptFriendRequests = false,
} = {}) {
  const requestId = `${owner}-${friend}-request`;
  await environment.withSecurityRulesDisabled(async (context) => {
    const firestore = context.firestore();
    await setDoc(doc(firestore, `profiles/${owner}`), makeProfile(owner, {
      sharePhotos,
      acceptFriendRequests,
    }));
    await setDoc(doc(firestore, `profiles/${friend}`), makeProfile(friend));
    await setDoc(doc(firestore, `friendRequests/${requestId}`), {
      fromUid: owner,
      toUid: friend,
      fromDisplayName: owner,
      toDisplayName: friend,
      status: "ACCEPTED",
      createdAt: Timestamp.now(),
      updatedAt: Timestamp.now(),
    });
    await setDoc(doc(firestore, `profiles/${owner}/friendAccess/${friend}`), {
      friendUid: friend,
      requestId,
      createdAt: Timestamp.now(),
    });
    await setDoc(doc(firestore, `profiles/${friend}/friendAccess/${owner}`), {
      friendUid: owner,
      requestId,
      createdAt: Timestamp.now(),
    });
    await setDoc(doc(firestore, `friendCodes/${friendCode}`), {
      uid: owner,
      displayName: owner,
    });
  });
  return { owner, friend, requestId };
}

test("guest and unverified users cannot read or create social data", async () => {
  const guest = environment.unauthenticatedContext().firestore();
  const unverified = signedIn("new-user", false).firestore();
  const profile = makeProfile("New driver", { acceptFriendRequests: true });

  await assertFails(getDoc(doc(guest, "profiles/new-user")));
  await assertFails(setDoc(doc(guest, "profiles/new-user"), profile));
  await assertFails(setDoc(doc(unverified, "friendCodes/ABCD2345EFGH"), {
    uid: "new-user",
    displayName: "New driver",
  }));
});

test("profile privacy defaults are enforced and disabled fields must be absent", async () => {
  const owner = signedIn("owner").firestore();
  const stranger = signedIn("stranger").firestore();
  const profileRef = doc(owner, "profiles/owner");

  await assertSucceeds(setDoc(profileRef, makeProfile("Owner")));
  await assertSucceeds(getDoc(profileRef));
  await assertFails(getDoc(doc(stranger, "profiles/owner")));
  await assertFails(updateDoc(profileRef, { level: 3, totalXp: 250 }));
  await assertFails(setDoc(doc(owner, "profiles/invalid"), makeProfile("Invalid", {
    level: 2,
    totalXp: 100,
  })));
});

test("friend-code lookup is exact and cannot be listed or used by unverified accounts", async () => {
  const { owner } = await seedFriendPair({ acceptFriendRequests: true });
  const verified = signedIn("lookup-user").firestore();
  const unverified = signedIn("unverified-user", false).firestore();

  await assertSucceeds(getDoc(doc(verified, `friendCodes/${friendCode}`)));
  await assertFails(getDocs(collection(verified, "friendCodes")));
  await assertFails(getDoc(doc(unverified, `friendCodes/${friendCode}`)));
  assert.equal(owner, "alice");
});

test("pending requests are visible only to their participants and do not grant profile access", async () => {
  const sender = signedIn("sender").firestore();
  const recipient = signedIn("recipient").firestore();
  const stranger = signedIn("stranger").firestore();
  const requestId = "pending-1";

  await environment.withSecurityRulesDisabled(async (context) => {
    const firestore = context.firestore();
    await setDoc(doc(firestore, "profiles/sender"), makeProfile("Sender"));
    await setDoc(doc(firestore, "profiles/recipient"), makeProfile("Recipient", {
      acceptFriendRequests: true,
    }));
    await setDoc(doc(firestore, `friendRequests/${requestId}`), {
      fromUid: "sender",
      toUid: "recipient",
      fromDisplayName: "Sender",
      toDisplayName: "Recipient",
      status: "PENDING",
      createdAt: Timestamp.now(),
    });
  });

  await assertSucceeds(getDoc(doc(sender, `friendRequests/${requestId}`)));
  await assertSucceeds(getDoc(doc(recipient, `friendRequests/${requestId}`)));
  await assertFails(getDoc(doc(stranger, `friendRequests/${requestId}`)));
  await assertFails(getDoc(doc(sender, "profiles/recipient")));
  await assertFails(setDoc(doc(recipient, "profiles/sender/friendAccess/recipient"), {
    friendUid: "recipient",
    requestId,
    createdAt: Timestamp.now(),
  }));
});

test("accepted friends can read the profile; strangers cannot", async () => {
  await seedFriendPair({ sharePhotos: true });
  const friend = signedIn("bob").firestore();
  const stranger = signedIn("mallory").firestore();

  await assertSucceeds(getDoc(doc(friend, "profiles/alice")));
  await assertFails(getDoc(doc(stranger, "profiles/alice")));
  await assertSucceeds(getDoc(doc(friend, "profiles/alice/friendAccess/bob")));
  await assertFails(getDoc(doc(stranger, "profiles/alice/friendAccess/bob")));
});

test("friend revocation and blocking must remove both access edges atomically", async () => {
  const { friend, owner, requestId } = await seedFriendPair({ sharePhotos: true });
  const ownerDb = signedIn(owner).firestore();
  const friendDb = signedIn(friend).firestore();
  const requestRef = doc(ownerDb, `friendRequests/${requestId}`);
  const ownerEdge = doc(ownerDb, `profiles/${owner}/friendAccess/${friend}`);
  const friendEdge = doc(ownerDb, `profiles/${friend}/friendAccess/${owner}`);
  const blockedRef = doc(ownerDb, `profiles/${owner}/blocked/${friend}`);
  const photoMetadata = doc(ownerDb, `profiles/${owner}/sharedSpots/spot-before-revocation`);
  await assertSucceeds(setDoc(photoMetadata, {
    brandName: "Nissan",
    modelName: "Skyline GT-R",
    rarity: "RARE",
    publishedAt: Timestamp.now(),
  }));
  await assertSucceeds(getDoc(doc(friendDb, `profiles/${owner}/sharedSpots/spot-before-revocation`)));

  await assertFails(deleteDoc(ownerEdge));
  await assertFails(deleteDoc(requestRef));
  await assertFails(setDoc(blockedRef, { createdAt: Timestamp.now() }));

  const batch = writeBatch(ownerDb);
  batch.delete(ownerEdge);
  batch.delete(friendEdge);
  batch.update(requestRef, { status: "REVOKED", updatedAt: Timestamp.now() });
  batch.set(blockedRef, { createdAt: Timestamp.now() });
  await assertSucceeds(batch.commit());

  await assertFails(getDoc(doc(friendDb, `profiles/${owner}`)));
  await assertFails(getDoc(doc(friendDb, `profiles/${owner}/sharedSpots/spot-before-revocation`)));
});

test("published photo metadata requires opt-in and is visible only to accepted friends", async () => {
  await seedFriendPair({ sharePhotos: true });
  const owner = signedIn("alice").firestore();
  const friend = signedIn("bob").firestore();
  const stranger = signedIn("mallory").firestore();
  const metadataPath = "profiles/alice/sharedSpots/spot-1";
  const metadata = {
    brandName: "Nissan",
    modelName: "Skyline GT-R",
    rarity: "RARE",
    publishedAt: Timestamp.now(),
  };

  await assertSucceeds(setDoc(doc(owner, metadataPath), metadata));
  await assertSucceeds(getDoc(doc(friend, metadataPath)));
  await assertFails(getDoc(doc(stranger, metadataPath)));
  await assertSucceeds(updateDoc(doc(owner, "profiles/alice"), { sharePhotos: false }));
  await assertFails(getDoc(doc(friend, metadataPath)));
});

test("published Spot metadata accepts only the documented projection", async () => {
  await seedFriendPair({ sharePhotos: true });
  const owner = signedIn("alice").firestore();
  const metadata = {
    brandName: "Nissan",
    modelName: "Skyline GT-R",
    rarity: "RARE",
    vehicleYear: 1999,
    publishedAt: Timestamp.now(),
  };

  await assertSucceeds(setDoc(doc(owner, "profiles/alice/sharedSpots/spot-1"), metadata));
  await assertFails(setDoc(doc(owner, "profiles/alice/sharedSpots/spot-private"), {
    ...metadata,
    note: "private note",
  }));
  await assertFails(setDoc(doc(owner, "profiles/alice/sharedSpots/spot-invalid-year"), {
    ...metadata,
    vehicleYear: 1800,
  }));
});
