# AI Chatbot Extension Guide

## Luong xu ly hien tai

1. `AiController` nhan cau hoi sync hoac stream tu `/api/v1/ai/ask`.
2. `AiService` lay history, goi router, chay tool, tao context, ghi audit.
3. `AiIntentRouterService` dinh tuyen intent bang rule chac chan, sau do moi goi model de sinh JSON intent, cuoi cung fallback heuristic.
4. `AiToolExecutorService` map intent sang truy van JDBC doc database nghiep vu.
5. `AiAnswerComposerService` uu tien formatter deterministic; neu chua co formatter thi dua context + tool JSON vao model de viet cau tra loi.
6. `AiAuditService` ghi intent, tham so, tool, data source, missing params, rows va loi vao `ai_audit_logs`.

## AI dang dua vao gi

- Prompt: router prompt bat model tra JSON intent; answer prompt bat model chi dung tool result JSON.
- Intent: `AiIntent` la danh sach question type backend ho tro.
- Rule: `AiIntentRouterService.deterministic` bat cac pattern tieng Viet/ma nghiep vu de on dinh hon model.
- Database: `AiToolExecutorService` doc bang `products`, `warehouses`, `locations`, `stock_levels`, `purchase_orders`, `inbound_receipts`, `sales_orders`, `picking_items`, `users`, `audit_logs`, v.v.
- API/model: `AiProviderRouterClient` chon Ollama hoac OpenAI-compatible theo config hoac request. Mac dinh dung model noi bo `stockmaster-ai`.
- Context: history gan nhat, intent parameters, tool data, data sources va missing params.
- Vector/RAG: chua co vector store/RAG; day la intent + SQL tool + prompt-grounded answer.

## Vi sao truoc day tra loi it

- Router bi hard-code trong mot file lon, kho thay thieu intent nao.
- `looksGreeting()` dung substring `hi`, nen cac tu nhu `hien`, `chi tiet` bi nham thanh chao hoi.
- Metadata intent, tool, data source, fallback nam rai rac o enum/router/executor/composer.
- Prompt router co danh sach intent thu cong, de lech voi enum va query thuc te.
- Audit chi luu intent/tool co ban, kho biet AI da dung bang nao va thieu tham so nao.

## Cau truc mo rong moi

- `AiIntentCatalog`: noi tap trung mo ta intent, domain, tool, data sources, required/optional params, example va fallback.
- `AiQueryContext`: context chuan truyen qua log/audit/prompt gom question, intent, params, tool, data sources, missing params, row count.
- `AiToolResult`: bo sung metadata `dataSources` va `missingParams`.

## Cach them nhom cau hoi moi

1. Them intent vao `AiIntent`.
2. Khai bao intent trong `AiIntentCatalog` voi domain, tool, data source, params va vi du cau hoi.
3. Them rule chac chan trong `AiIntentRouterService` neu cau hoi co keyword/ma nghiep vu ro rang.
4. Them case trong `AiToolExecutorService.execute`.
5. Viet method query rieng, chi doc du lieu can thiet va gioi han `LIMIT`.
6. Them formatter trong `AiAnswerComposerService` neu cau tra loi can format on dinh.
7. Them test router va, neu co the, test executor/composer.
8. Chay `.\mvnw.cmd test` va xem `ai_audit_logs` de debug.

## Test case mau

- San pham nao sap het hang?
- Kho nao con nhieu hang nhat?
- Hom nay co bao nhieu don nhap?
- San pham A dang nam o vi tri nao?
- Ton kho hien tai cua SKU 00018 la bao nhieu?
- Don xuat nao dang cho xu ly?
- Nhan vien nao xu ly don nhap gan nhat?
- Co san pham nao chua duoc gan vi tri khong?
- Thong ke nhap xuat theo ngay/thang.
- Goi y nhap them hang dua tren ton kho thap.

## Fallback

- Thieu tham so bat buoc: tra loi can SKU, kho, don hang hoac khoang thoi gian cu the.
- Tool khong co dong du lieu: tra fallback theo intent trong composer/catalog.
- Ngoai pham vi WMS hoac yeu cau thao tac tao/sua/xoa/duyet: tra `UNSUPPORTED`.
- Model loi: router fallback heuristic, composer tra thong bao khong truy van duoc.
