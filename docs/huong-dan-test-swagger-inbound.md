# Huong dan test luong nhap hang tren Swagger

Tai lieu nay huong dan chay service va test luong nhap hang end-to-end bang Swagger cho du an.

## 0) Chon cach test de tranh loi 401

Ban co 2 cach test:

1. Test nhanh khong can token (khuyen nghi khi demo backend): vao Swagger cua inbound-service truc tiep
  - http://localhost:9004/swagger-ui/index.html
2. Test qua API Gateway (thuc te he thong):
  - http://localhost:9000/swagger-ui.html
  - Co the gap 401 neu endpoint bi bao ve va ban chua dang nhap/gan token.

De demo nghiep vu nhap hang nhanh, nen dung cach 1 truoc.

## 1) Can chay nhung module nao

De test duoc luong inbound qua Gateway, can bat toi thieu 4 module:

1. `eureka-server`
2. `warehouse-service`
3. `inbound-service`
4. `api-gateway`

Neu chi test rieng API tung service (khong qua gateway), ban co the mo swagger cua service do tren port rieng.

## 2) Chuan bi truoc khi chay

1. Dam bao Postgres dang chay va cac bien moi truong DB da dung (co the dat trong `.env`).
2. Tu thu muc goc, co the nap bien moi truong:

```powershell
.\load-env.ps1
```

3. Nen build nhanh de check loi compile:

```powershell
.\mvnw.cmd -DskipTests compile
```

## 3) Cach chay cac module

Mo 4 terminal rieng, deu tai thu muc goc repo, roi chay lan luot:

Terminal 1:

```powershell
.\mvnw.cmd -f eureka-server\pom.xml spring-boot:run
```

Terminal 2:

```powershell
.\mvnw.cmd -f warehouse-service\pom.xml spring-boot:run
```

Terminal 3:

```powershell
.\mvnw.cmd -f inbound-service\pom.xml spring-boot:run
```

Terminal 4:

```powershell
.\mvnw.cmd -f api-gateway\pom.xml spring-boot:run
```

Luu y: Khong dung `-pl ... -am spring-boot:run` o root project cha, vi se de loi
`Unable to find a suitable main class`.

## 4) Link Swagger can mo

Swagger tong hop qua Gateway:

- http://localhost:9000/swagger-ui.html

Swagger rieng cua inbound-service (de test nhanh, tranh auth tu gateway):

- http://localhost:9004/swagger-ui/index.html

Trong swagger tong hop, chon nhom:

1. `Kho & Ton`
2. `Nhap hang`

## 5) Kich ban test luong nhap hang (de xong 1 vong nghiep vu)

Muc tieu: Tao PO -> Tao PO item -> Confirm PO -> Receive -> Complete putaway -> Kiem tra ton kho.

### Buoc A - Lay ID kho va vi tri

Trong nhom `Kho & Ton`:

1. Goi `GET /api/warehouses` de lay `warehouseId`.
2. Goi `GET /api/locations?warehouseId=...` de lay `locationId` thuoc kho vua chon.

Ghi lai:

- `warehouseId`
- `locationId`

### Buoc B - Tao Purchase Order

Trong nhom `Nhap hang`, goi `POST /api/purchase-orders`:

```json
{
  "poNumber": "PO-TEST-001",
  "supplierId": "11111111-1111-1111-1111-111111111111",
  "warehouseId": "<warehouseId>",
  "orderDate": "2026-03-25",
  "expectedDate": "2026-03-27",
  "totalAmount": 0
}
```

Lay `poId` tu response (`data.id`).

### Buoc C - Tao dong hang PO

Goi `POST /api/po-items`:

```json
{
  "purchaseOrderId": "<poId>",
  "lineNumber": 1,
  "productId": "22222222-2222-2222-2222-222222222222",
  "productSku": "SKU-DEMO-001",
  "orderedQty": 10,
  "unitPrice": 10000
}
```

Lay `poItemId` tu response (`data.id`).

### Buoc D - Confirm PO truoc khi nhan hang

Goi `POST /api/purchase-orders/{id}/confirm` voi `id = <poId>`.

Ky vong:

- Status PO chuyen sang `RECEIVING`.

### Buoc E - Receive hang theo dong

Goi `POST /api/po-items/{id}/receive` voi `id = <poItemId>`:

```json
{
  "qty": 10,
  "suggestedLocationId": "<locationId>"
}
```

Ky vong:

1. `receivedQty` cua PO item tang len 10.
2. He thong sinh 1 putaway task (status `PENDING`).

Lay `putawayTaskId` tu response (`data.putawayTask.id`).

### Buoc F - Complete putaway de cong ton

Goi `POST /api/putaway-tasks/{id}/complete` voi `id = <putawayTaskId>`:

```json
{
  "actualLocationId": "<locationId>"
}
```

Ky vong:

1. Putaway status thanh `COMPLETED`.
2. Ton kho duoc cong o location da chon.

### Buoc G - Kiem tra ton kho

Trong nhom `Kho & Ton`, goi:

- `GET /api/stocks?warehouseId=<warehouseId>&locationId=<locationId>&productId=<productId>`

Ky vong:

- Co ban ghi ton cua `productId` tai location, so luong tang theo qty da complete.

### Buoc H - Kiem tra PO da hoan tat

Goi `GET /api/purchase-orders/{id}` voi `id = <poId>`.

Ky vong:

- Neu tat ca line da nhan du, PO status = `RECEIVED`.

### Buoc I - Kiem tra API detail cho FE

Goi `GET /api/purchase-orders/{id}/detail` voi `id = <poId>`.

Ky vong:

1. `data.purchaseOrder.id` = `poId`
2. `data.items` co line vua tao
3. `data.putawayTasks` co task vua complete
4. `data.totalOrderedQty` = tong orderedQty
5. `data.totalReceivedQty` = tong receivedQty
6. `data.fullyReceived` = `true` khi da nhan du tat ca line

## 6.1) Checklist ket qua dung/sai de bao cao nhanh

Sau moi buoc, ban doi chieu nhanh theo bang sau:

1. Sau confirm:
  - Dung: `purchaseOrder.status = RECEIVING`
  - Sai: status van `DRAFT` hoac request fail
2. Sau receive:
  - Dung: `poItem.receivedQty` tang dung bang `qty`
  - Dung: co `putawayTask` moi, `status = PENDING`
  - Sai: `receivedQty` khong doi hoac khong sinh task
3. Sau complete putaway:
  - Dung: `putawayTask.status = COMPLETED`
  - Dung: `actualLocationId` co gia tri
  - Dung: `completedAt` khac `null`
  - Sai: van `PENDING/IN_PROGRESS`
4. Sau kiem tra stock:
  - Dung: ton kho tang dung theo qty putaway
  - Sai: khong co ban ghi hoac qty khong khop
5. Sau kiem tra PO:
  - Dung: PO len `RECEIVED` neu tat ca line da nhan du
  - Sai: da nhan du nhung PO khong len `RECEIVED`

## 6) Cac case loi nen test de bao cao

1. Receive truoc khi confirm PO -> phai bi chan.
2. Receive vuot `orderedQty` -> phai bi chan.
3. Complete putaway 2 lan -> lan 2 phai bi chan.
4. Xoa po-item da co `receivedQty > 0` -> phai bi chan.
5. Huy PO da `RECEIVED` -> phai bi chan.

## 7) Neu mo Swagger khong thay API

1. Kiem tra 4 module da run chua.
2. Kiem tra Eureka co len: http://localhost:8761
3. Kiem tra Gateway da len: http://localhost:9000/swagger-ui.html
4. Kiem tra log Gateway co route toi `inbound-service` va `warehouse-service`.

## 8) Lenh test nhanh module inbound

```powershell
.\mvnw.cmd -f inbound-service\pom.xml test
```

## 9) Neu can reset nhanh de test lai tu dau

1. Dung cac service dang chay.
2. Doi `poNumber` moi (vi du `PO-TEST-002`) de tranh trung du lieu.
3. Chay lai theo thu tu o muc 3.
