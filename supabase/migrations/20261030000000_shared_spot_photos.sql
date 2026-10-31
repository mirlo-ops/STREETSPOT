insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'shared-spot-photos',
  'shared-spot-photos',
  false,
  5242880,
  array['image/jpeg']
)
on conflict (id) do update
set
  name = excluded.name,
  public = false,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

-- Storage has no client-facing policies. All file operations pass through the
-- shared-photos Edge Function, which checks Firebase identity and Firestore ACLs.
