# Nebula Eye

Olho + mão para automação local no Android, sem root.

- **Olho**: captura de tela via MediaProjection (`GET /screenshot` → JPEG) + árvore de UI via AccessibilityService (`GET /nodes` → JSON).
- **Mão**: gestos via AccessibilityService (`POST /tap`, `/swipe`, `/text`).
- **Transporte**: HTTP em `http://127.0.0.1:38177`. Nada sai do aparelho.

## Uso (uma vez)

1. Instale o APK, abra o app.
2. `1. Permitir captura de tela` → aceite o diálogo do sistema.
3. `2. Ativar acessibilidade` → ligue **Nebula Eye** na lista.
4. `3. Ligar servidor`.

## Endpoints

```
GET  /state       -> {"ok":true,"projection":true,"a11y":true,"w":1080,"h":2316}
GET  /screenshot  -> image/jpeg
GET  /nodes       -> {"ok":true,"nodes":[{"cls","b":[l,t,r,b],"c","e","f","t","d","id"}]}
POST /tap    x,y            -> {"ok":true}
POST /swipe  x1,y1,x2,y2,ms -> {"ok":true}
POST /text   text=...       -> {"ok":true}
```

Aceita query string ou JSON.

## Notas

- Enquanto o serviço de acessibilidade estiver ativo, bancos/marketplaces
  (Shopee/Maree, Caixa) podem bloquear o app deles. Desligue quando for usar.
- Debug build: CI gera `app-debug.apk` em Actions → Artifacts.
