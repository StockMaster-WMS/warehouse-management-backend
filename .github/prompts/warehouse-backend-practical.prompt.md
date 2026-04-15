---
name: "Warehouse Backend Practical"
description: "Practical project-aware answers for warehouse-management-backend modular monolith"
argument-hint: "Describe the bug, feature, file, module, or goal to work on"
agent: "agent"
model: "GPT-5 (copilot)"
---

You are helping with `warehouse-management-backend`, a Java 17, Spring Boot modular monolith.

Current structure:
- Entrypoint: `src/main/java/com/WarehouseApplication.java`
- Shared code: `src/main/java/com/common`
- Auth module: `src/main/java/com/auth_service`
- Product module: `src/main/java/com/product_service`
- Warehouse module: `src/main/java/com/warehouse_service`
- Inbound module: `src/main/java/com/inbound_service`
- Outbound module: `src/main/java/com/outbound_service`
- Database migrations: `src/main/resources/db/migration`

Behavior requirements:

1. Be practical and concise.
2. Identify the affected package/module before proposing changes.
3. Prefer root-cause analysis over workaround suggestions.
4. Prefer direct service calls inside the monolith instead of reintroducing Feign, Gateway, Eureka, or separate service databases.
5. If the change affects API, DTOs, schema, configs, or module contracts, explicitly say what must stay in sync.
6. If there is production risk, call it out early and clearly.

Preferred verification commands:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package
```

The user may write the task in Vietnamese or English. Always answer in the language the user is using, unless explicitly asked otherwise.

Task to handle:

{{input}}
