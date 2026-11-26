// Arma un JWT de mentira (firma falsa) con los claims dados -- el frontend
// nunca verifica la firma (ver decodificarJwt en api/client.js), solo lee claims.
export function jwtFalso(claims) {
  const b64 = (obj) =>
    btoa(unescape(encodeURIComponent(JSON.stringify(obj))))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '')
  return `${b64({ alg: 'HS256' })}.${b64(claims)}.firma-falsa`
}
