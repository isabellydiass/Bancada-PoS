# Bancada PoS frontend

Angular 21 with Node.js 22.12 or newer in the 22.x series. Nginx serves the Angular build and proxies the same-origin API in the test environment.

Frontend checks from this directory:

```sh
npm ci
npm test -- --watch=false
npm run build
```
