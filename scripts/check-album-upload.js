const fs = require("fs");
const path = require("path");

function readEnv(file) {
  const env = {};
  const lines = fs.readFileSync(file, "utf8").split(/\r?\n/);
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#") || !trimmed.includes("=")) continue;
    const key = trimmed.slice(0, trimmed.indexOf("=")).trim();
    const value = trimmed
      .slice(trimmed.indexOf("=") + 1)
      .trim()
      .replace(/^['"]|['"]$/g, "");
    env[key] = value;
  }
  return env;
}

async function main() {
  const envPath = path.join(process.env.LOCALAPPDATA, "PrettyBoothDesktop", ".env");
  const env = readEnv(envPath);
  const base = env.WEB_ALBUM_BASE_URL.replace(/\/a\/?$/, "").replace(/\/$/, "");
  const session = `desktop-e2e-${Date.now()}`;

  // Tiny JPEG for API smoke test.
  const jpeg = Buffer.from(
    "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAX/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAH/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oACAEBAAEFAqf/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAEDAQE/ASP/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oACAECAQE/ASP/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oACAEBAAY/Al//xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oACAEBAAE/IV//2gAMAwEAAgADAAAAEP/EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQMBAT8QH//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQIBAT8QH//EABQQAQAAAAAAAAAAAAAAAAAAABD/2gAIAQEAAT8QH//Z",
    "base64"
  );

  const form = new FormData();
  form.append("upload_preset", env.CLOUDINARY_UPLOAD_PRESET);
  form.append("file", new Blob([jpeg], { type: "image/jpeg" }), "desktop_e2e.jpg");

  const uploadResponse = await fetch(
    `https://api.cloudinary.com/v1_1/${env.CLOUDINARY_CLOUD_NAME}/image/upload`,
    { method: "POST", body: form }
  );
  const upload = await uploadResponse.json().catch(() => ({}));
  console.log(`CLOUDINARY_STATUS=${uploadResponse.status}`);
  if (!uploadResponse.ok) {
    console.log(`CLOUDINARY_ERROR=${upload.error?.message || "unknown"}`);
    process.exit(1);
  }

  const createResponse = await fetch(`${base}/api/v1/albums`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${env.BOOTH_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      externalSessionId: session,
      expectedAssets: 1,
      expiresInDays: 1,
    }),
  });
  const album = await createResponse.json().catch(() => ({}));
  console.log(`CREATE_STATUS=${createResponse.status}`);
  if (!createResponse.ok) {
    console.log(`CREATE_ERROR=${album.error || "unknown"}`);
    process.exit(1);
  }

  const finalizeBody = {
    kind: "ORIGINAL",
    position: 0,
    assetId: upload.asset_id || upload.public_id,
    publicId: upload.public_id,
    version: String(upload.version || 1),
    format: upload.format || "jpg",
    resourceType: upload.resource_type || "image",
    deliveryType: upload.type || "upload",
    width: upload.width || 0,
    height: upload.height || 0,
    bytes: upload.bytes || jpeg.length,
  };

  const finalizeResponse = await fetch(
    `${base}/api/v1/albums/${encodeURIComponent(album.albumId)}/assets`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${env.BOOTH_API_KEY}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(finalizeBody),
    }
  );
  const finalized = await finalizeResponse.json().catch(() => ({}));
  console.log(`FINALIZE_STATUS=${finalizeResponse.status}`);
  if (!finalizeResponse.ok) {
    console.log(`FINALIZE_ERROR=${finalized.error || "unknown"}`);
    process.exit(1);
  }

  const completeResponse = await fetch(
    `${base}/api/v1/albums/${encodeURIComponent(album.albumId)}/complete`,
    {
      method: "POST",
      headers: { Authorization: `Bearer ${env.BOOTH_API_KEY}` },
    }
  );
  const completed = await completeResponse.json().catch(() => ({}));
  console.log(`COMPLETE_STATUS=${completeResponse.status}`);
  if (!completeResponse.ok) {
    console.log(`COMPLETE_ERROR=${completed.error || "unknown"}`);
    process.exit(1);
  }

  const token = new URL(album.albumUrl).pathname.split("/").pop();
  const publicResponse = await fetch(
    `${base}/api/v1/public/albums/${encodeURIComponent(token)}`
  );
  const manifest = await publicResponse.json().catch(() => ({}));
  console.log(`PUBLIC_STATUS=${publicResponse.status}`);
  console.log(`PUBLIC_ASSETS=${Array.isArray(manifest.assets) ? manifest.assets.length : "?"}`);
}

main().catch((error) => {
  console.log(`ERROR=${error.message}`);
  process.exit(1);
});
