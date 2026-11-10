# Bancada PoS frontend

Angular 21 with Node.js 22.12 or newer in the 22.x series. Run the complete demo from the repository root using Docker Compose. Nginx serves the production Angular build and proxies the same-origin API.

Frontend checks from this directory:

```sh
npm ci
npm test -- --watch=false
npm run build
```
