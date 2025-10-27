const base = "http://localhost:8080";

function headers(jwt?: string) {
  return {
    "Content-Type": "application/json",
    ...(jwt ? { Authorization: `Bearer ${jwt}` } : {}),
  };
}

export async function apiGet<T>(path: string, jwt?: string): Promise<T> {
  const r = await fetch(`${base}${path}`, { headers: headers(jwt) });
  if (!r.ok) throw new Error(await r.text());
  return r.json() as Promise<T>;
}

export async function apiPost<T, B = unknown>(
  path: string,
  body?: B,
  jwt?: string
): Promise<T> {
  const r = await fetch(`${base}${path}`, {
    method: "POST",
    headers: headers(jwt),
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!r.ok) throw new Error(await r.text());
  return r.json() as Promise<T>;
}
