package com.inbound_service.service;

import com.common.audit.AuditLogService;
import com.common.notification.NotificationService;
import com.inbound_service.dto.request.CreateRmaRequest;
import com.inbound_service.entity.Rma;
import com.inbound_service.entity.RmaItem;
import com.inbound_service.repository.RmaRepository;
import com.outbound_service.repository.CustomerRepository;
import com.outbound_service.repository.PickingItemRepository;
import com.outbound_service.repository.SalesOrderRepository;
import com.product_service.entity.Product;
import com.product_service.entity.Supplier;
import com.product_service.repository.ProductRepository;
import com.product_service.repository.SupplierRepository;
import com.product_service.service.ProductService;
import com.warehouse_service.entity.Location;
import com.warehouse_service.entity.StockLevel;
import com.warehouse_service.entity.Warehouse;
import com.warehouse_service.repository.LocationRepository;
import com.warehouse_service.repository.StockLevelRepository;
import com.warehouse_service.repository.WarehouseRepository;
import com.warehouse_service.service.StockLevelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RmaServiceSupplierReturnStockTest {

    private RmaRepository rmaRepository;
    private StockLevelService stockLevelService;
    private WarehouseRepository warehouseRepository;
    private LocationRepository locationRepository;
    private StockLevelRepository stockLevelRepository;
    private ProductRepository productRepository;
    private SupplierRepository supplierRepository;
    private RmaService rmaService;

    private UUID warehouseId;
    private UUID locationId;
    private UUID productId;
    private UUID supplierId;
    private Warehouse warehouse;
    private Location location;
    private Product product;
    private StockLevel stock;

    @BeforeEach
    void setUp() {
        rmaRepository = mock(RmaRepository.class);
        stockLevelService = mock(StockLevelService.class);
        NotificationService notificationService = mock(NotificationService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        warehouseRepository = mock(WarehouseRepository.class);
        locationRepository = mock(LocationRepository.class);
        stockLevelRepository = mock(StockLevelRepository.class);
        ProductService productService = mock(ProductService.class);
        productRepository = mock(ProductRepository.class);
        supplierRepository = mock(SupplierRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        PickingItemRepository pickingItemRepository = mock(PickingItemRepository.class);

        rmaService = new RmaService(
                rmaRepository,
                stockLevelService,
                notificationService,
                auditLogService,
                warehouseRepository,
                locationRepository,
                stockLevelRepository,
                productService,
                productRepository,
                supplierRepository,
                customerRepository,
                salesOrderRepository,
                pickingItemRepository);

        warehouseId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        productId = UUID.randomUUID();
        supplierId = UUID.randomUUID();
        warehouse = Warehouse.builder().id(warehouseId).code("WH1").name("Kho 1").build();
        location = Location.builder()
                .id(locationId)
                .warehouse(warehouse)
                .code("A1-01")
                .zone("A")
                .aisle("1")
                .rack("1")
                .level((short) 1)
                .bin("01")
                .build();
        Supplier supplier = Supplier.builder().id(supplierId).code("SUP1").name("NCC 1").build();
        product = Product.builder()
                .id(productId)
                .sku("SKU-1")
                .name("Sản phẩm 1")
                .primarySupplier(supplier)
                .status("ACTIVE")
                .build();
        stock = StockLevel.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .location(location)
                .productId(productId)
                .lotNumber("LOT-1")
                .qtyOnHand(10)
                .qtyReserved(0)
                .build();

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(locationRepository.findAllById(List.of(locationId))).thenReturn(List.of(location));
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(stockLevelRepository.findByLocationIdAndProductIdAndLotNumber(locationId, productId, "LOT-1"))
                .thenReturn(Optional.of(stock));
    }

    @Test
    void createSupplierReturnDoesNotDeductStockBeforeApproval() {
        CreateRmaRequest request = new CreateRmaRequest(
                "SUPPLIER",
                null,
                supplierId,
                null,
                null,
                null,
                warehouseId,
                "Trả nhà cung cấp",
                List.of(new CreateRmaRequest.ItemRequest(productId, null, 2, "LOT-1", locationId)));
        when(rmaRepository.save(any(Rma.class))).thenAnswer(invocation -> {
            Rma rma = invocation.getArgument(0);
            rma.setId(UUID.randomUUID());
            rma.getItems().forEach(item -> {
                item.setId(UUID.randomUUID());
                item.setRma(rma);
                item.setReceivedQty(0);
            });
            return rma;
        });

        rmaService.createSupplierReturn(request, UUID.randomUUID());

        verify(stockLevelService, never()).adjust(any());
    }

    @Test
    void approveSupplierReturnDeductsStock() {
        UUID rmaId = UUID.randomUUID();
        Rma rma = Rma.builder()
                .id(rmaId)
                .rmaNumber("RMA-1")
                .returnType("SUPPLIER")
                .supplierId(supplierId)
                .supplierName("NCC 1")
                .warehouseId(warehouseId)
                .status(Rma.RmaStatus.REQUESTED)
                .items(List.of(RmaItem.builder()
                        .id(UUID.randomUUID())
                        .productId(productId)
                        .expectedQty(2)
                        .receivedQty(0)
                        .lotNumber("LOT-1")
                        .returnLocationId(locationId)
                        .build()))
                .build();
        rma.getItems().forEach(item -> item.setRma(rma));
        when(rmaRepository.findByIdWithItemsForUpdate(rmaId)).thenReturn(Optional.of(rma));
        when(rmaRepository.save(any(Rma.class))).thenAnswer(invocation -> invocation.getArgument(0));

        rmaService.approve(rmaId, UUID.randomUUID(), null);

        verify(stockLevelService).adjust(any());
    }
}
