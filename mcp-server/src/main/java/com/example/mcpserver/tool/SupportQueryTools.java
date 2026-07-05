package com.example.mcpserver.tool;


import com.example.mcpserver.dto.SupportDtos;
import com.example.mcpserver.entity.*;
import com.example.mcpserver.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 唯讀 MCP 工具：Agent 在採取行動前用來理解電子郵件的所有查詢方法——
 * 識別顧客身份、取得訂單與商品資訊、偵測重複扣款、確認保固期限，
 * 以及查閱歷史工單以偵測重複性故障。此類別中的所有方法均不會修改資料狀態。
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SupportQueryTools {

    private final CustomerRepository customers;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final SupportTicketRepository tickets;

    // ---------------------------------------------------------------------
    // 識別顧客身份
    // ---------------------------------------------------------------------
    @McpTool(name = "lookup_customer_by_email",
            description = "依電子郵件地址查詢顧客帳號。回傳姓名、聯絡方式、慣用語言及會員等級。請優先呼叫此工具以識別來信顧客身份。")
    public SupportDtos.CustomerInfo lookupCustomerByEmail(
            @McpToolParam(description = "顧客的電子郵件地址，例如 sarah.mitchell@example.com")
            String email) {
        // 1. 檢查顧客是否存在
        Customer c = customers.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "找不到此電子郵件對應的顧客帳號：" + email));
        // 2. 將 Customer 實體轉換為 CustomerInfo DTO
        return toCustomerInfo(c);
    }

    // ---------------------------------------------------------------------
    // 取得顧客訂單 by email
    // ---------------------------------------------------------------------
    @McpTool(name = "get_customer_orders_by_email",
            description = "列出顧客的所有訂單（最新在前），每筆訂單包含商品明細及付款記錄。可用於找出來信所指的訂單，或查閱顧客的購買歷程。")
    public List<SupportDtos.OrderDetails> getCustomerOrders(
            @McpToolParam(description = "顧客的電子郵件地址")
            String email) {
        // 依電郵查出所有訂單（最新在前），逐筆轉為 DTO 後回傳
        return orders.findByCustomerEmailIgnoreCaseOrderByOrderDateDesc(email.trim())
                .stream()
                .map(this::toOrderDetails)
                .toList();
    }

    // ---------------------------------------------------------------------
    // 取得顧客訂單 by order number
    // ---------------------------------------------------------------------
    @McpTool(name = "get_customer_orders_by_order_number",
            description = "依訂單編號（顧客來信所引用的號碼，例如「#4471」）查詢單筆訂單，包含商品明細及所有相關付款記錄。")
    public SupportDtos.OrderDetails getOrderByNumber(
            @McpToolParam(description = "訂單編號，僅含數字，例如 4471")
            String orderNumber) {
        return toOrderDetails(requireOrder(orderNumber));
    }

    // ---------------------------------------------------------------------
    // 商品查詢 / 售前問題 by name or sku
    // ---------------------------------------------------------------------
    @McpTool(name = "search_products_by_name_or_sku",
            description = "依商品名稱或 SKU 片段搜尋商品目錄。可用於回答售前問題，或識別顧客所描述的商品。")
    public List<SupportDtos.ProductInfo> searchProducts(
            @McpToolParam(description = "商品名稱或 SKU 片段，例如「X200」或「blender」")
            String query) {
        // 1. 檢查輸入是否空白
        String q = query.trim();

        // 2. 搜尋商品目錄
        return products.findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(q, q)
                .stream()
                .map(this::toProductInfo)
                .toList();
    }

    // ---------------------------------------------------------------------
    // 商品查詢 / 售前問題 by sku
    // ---------------------------------------------------------------------
    @McpTool(name = "get_product_by_sku",
            description = "依 SKU 查詢單一商品的完整資訊，包含規格 JSON（電壓、尺寸、材質等）。可用於回答規格相關問題，例如商品是否支援歐規電壓。")
    public SupportDtos.ProductInfo getProductBySku(
            @McpToolParam(description = "商品 SKU，例如 X200 或 BLND-300")
            String sku) {
        Product p = products.findBySkuIgnoreCase(sku.trim())
                .orElseThrow(() -> new IllegalArgumentException("找不到此 SKU 對應的商品：" + sku));
        return toProductInfo(p);
    }

    // ---------------------------------------------------------------------
    // 帳務：重複扣款偵測
    // ---------------------------------------------------------------------
    @McpTool(name = "detect_duplicate_charges_by_order_number",
            description = "分析訂單的付款記錄以偵測重複扣款。比對實際扣款總額與訂單金額，並標記相同金額的重複付款。當顧客反映被重複收費時使用。")
    public SupportDtos.DuplicateChargeResult detectDuplicateCharges(
            @McpToolParam(description = "要檢查的訂單編號，例如 4471")
            String orderNumber) {

        // 1. 取得訂單
        CustomerOrder order = requireOrder(orderNumber);

        // 2. 過濾出已完成扣款的付款記錄
        List<Payment> captured = payments.findByOrderOrderNumberOrderByChargedAtAsc(orderNumber.trim())
                .stream()
                .filter(p -> p.getStatus() == Enums.PaymentStatus.CAPTURED)
                .toList();

        // 3. 計算實際扣款總額及與訂單金額的差異
        BigDecimal expected = order.getTotalAmount();
        BigDecimal totalCharged = captured.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add); // 將所有 CAPTURED 付款金額累加，得出「實際被扣總額」
        BigDecimal overcharged = totalCharged.subtract(expected).max(BigDecimal.ZERO);

        // 4. 判斷是否有重複扣款 (有兩筆以上 CAPTURED 付款記錄，且實際扣款總額大於訂單金額)
        boolean duplicate = captured.size() > 1 && overcharged.signum() > 0;

        // 5. 生成摘要
        String summary = duplicate
                ? "偵測到重複扣款：訂單 %s 共被扣款 %d 次，合計 %s %s，但訂單總額僅為 %s %s——超收 %s %s。請退還其中一筆款項。"
                .formatted(orderNumber, captured.size(), totalCharged, order.getCurrency(),
                        expected, order.getCurrency(), overcharged, order.getCurrency())
                : "未發現重複扣款：訂單 %s 共有 %d 筆已完成付款，合計 %s %s，與訂單總額 %s %s 相符。"
                .formatted(orderNumber, captured.size(), totalCharged, order.getCurrency(),
                        expected, order.getCurrency());

        // 6. 回傳結果
        return new SupportDtos.DuplicateChargeResult(orderNumber, duplicate, captured.size(), totalCharged,
                expected, overcharged, captured.stream().map(this::toPaymentInfo).toList(), summary);
    }


    // ---------------------------------------------------------------------
    // 歷史記錄：重複性故障偵測
    // ---------------------------------------------------------------------
    @McpTool(name = "get_customer_ticket_history_by_email",
            description = "取得顧客的歷史工單（最新在前），以便識別重複性故障或反覆出現的客訴——例如同一商品第三次損壞，此情況應給予善意補償。")
    public SupportDtos.TicketHistory getCustomerTicketHistory(
            @McpToolParam(description = "顧客的電子郵件地址")
            String email) {

        // 1. 依 email 查出所有歷史工單（最新在前）
        List<SupportTicket> found =
                tickets.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(email.trim());

        // 2. 將 Entity → DTO
        List<SupportDtos.TicketInfo> infos = found.stream().map(this::toTicketInfo).toList();

        // 3. 組裝結果（含自然語言摘要）
        String summary = "顧客 %s 共有 %d 筆歷史工單。".formatted(email, found.size());
        return new SupportDtos.TicketHistory(email, found.size(), infos, summary);
    }

    // ---------------------------------------------------------------------
    // 保固期確認
    // ---------------------------------------------------------------------
    @McpTool(name = "check_warranty_by_order_number_and_sku",
            description = "依訂單日期加上商品保固期限，確認訂單中的商品是否仍在保固期內。在核准保固退款或換貨前請先呼叫此工具。")
    public SupportDtos.WarrantyStatus checkWarranty(
            @McpToolParam(description = "商品所屬的訂單編號，例如 4198")
            String orderNumber,
            @McpToolParam(required = false, description = "要查詢的商品 SKU；若訂單只有一件商品則可省略")
            String sku) {

        // 1. 取得訂單
        CustomerOrder order = requireOrder(orderNumber);

        // 2. 從訂單中定位目標商品
        OrderItem item = resolveItem(order, sku);
        Product product = item.getProduct();

        // 3. 計算保固到期日
        LocalDate end = order.getOrderDate().plusMonths(product.getWarrantyMonths()); // 保固到期日 = 購買日 + 保固月數，例如 2024-01-15 + 12個月 = 2025-01-15

        // 4. 判斷今天是否仍在保固期內
        boolean inWarranty = !LocalDate.now().isAfter(end); // 今天不晚於到期日 → 仍在保固期（包含到期當天）

        // 5. 組裝結果
        String summary = inWarranty
                ? "%s 在訂單 %s 中仍在保固期內（購買日期：%s，%d 個月保固，到期日：%s）。"
                .formatted(product.getName(), orderNumber, order.getOrderDate(),
                        product.getWarrantyMonths(), end)
                : "%s 在訂單 %s 中已超出保固期（購買日期：%s，保固到期日：%s）。"
                .formatted(product.getName(), orderNumber, order.getOrderDate(), end);

        return new SupportDtos.WarrantyStatus(orderNumber, product.getSku(), product.getName(),
                order.getOrderDate(), product.getWarrantyMonths(), end, inWarranty, summary);
    }


    // ---------------------------------------------------------------------
    // 內部輔助方法
    // ---------------------------------------------------------------------

    /**
     * 依訂單編號取得訂單，找不到時拋出例外，供多個工具共用以避免重複的查詢與錯誤處理邏輯。
     */
    private CustomerOrder requireOrder(String orderNumber) {
        return orders.findByOrderNumber(orderNumber.trim())
                .orElseThrow(() -> new IllegalArgumentException("找不到此訂單編號：" + orderNumber));
    }

    /**
     * 從訂單中解析出目標 OrderItem。
     * SKU 為 null 或空白時，訂單只能有一件商品，否則拋出例外要求呼叫方明確指定 SKU。
     * 用於保固查詢前確保能精確定位到單一商品。
     */
    private OrderItem resolveItem(CustomerOrder order, String sku) {
        List<OrderItem> items = order.getItems();
        if (items.isEmpty()) {
            throw new IllegalArgumentException("訂單 " + order.getOrderNumber() + " 沒有任何商品明細。");
        }
        if (sku == null || sku.isBlank()) {
            if (items.size() > 1) {
                throw new IllegalArgumentException("訂單 " + order.getOrderNumber()
                        + " 包含多件商品，請指定 SKU 以查詢保固。");
            }
            return items.get(0);
        }
        return items.stream()
                .filter(i -> i.getProduct().getSku().equalsIgnoreCase(sku.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "訂單 " + order.getOrderNumber() + " 不包含 SKU 為 " + sku + " 的商品。"));
    }


    /**
     * 將 Customer 實體轉換為扁平的 DTO，避免 Hibernate Proxy 在序列化時逃逸。
     */
    private SupportDtos.CustomerInfo toCustomerInfo(Customer c) {
        return new SupportDtos.CustomerInfo(c.getId(), c.getFullName(), c.getEmail(), c.getPhone(),
                c.getPreferredLanguage(), c.getLoyaltyTier().name());
    }

    /**
     * 將 Product 實體轉換為扁平的 DTO，specifications 以原始 JSON 字串直接傳遞。
     */
    private SupportDtos.ProductInfo toProductInfo(Product p) {
        return new SupportDtos.ProductInfo(p.getId(), p.getSku(), p.getName(), p.getDescription(),
                p.getCategory(), p.getPrice(), p.getCurrency(), p.getSpecifications(),
                p.getWarrantyMonths(), p.getStockQuantity());
    }

    /**
     * 將 CustomerOrder 及其關聯的 items、payments 一次展開為單一扁平 DTO，供 Agent 一次讀取全部資訊。
     * 資料流程圖
     * <p>
     * CustomerOrder (o)
     * │
     * ├─ o.getItems()     → List<OrderItem>   → List<OrderItemInfo>  ─┐
     * │       └─ i.getProduct()  → Product                            │
     * │                                                               ▼
     * ├─ o.getPayments()  → List<Payment>     → List<PaymentInfo>  → OrderDetails
     * │                                                               ▲
     * └─ o.getCustomer()  → Customer          → fullName, email ──────┘
     */
    private SupportDtos.OrderDetails toOrderDetails(CustomerOrder o) {
        // 1. 展開商品明細
        List<SupportDtos.OrderItemInfo> items = o.getItems().stream()
                .map(i -> new SupportDtos.OrderItemInfo(i.getProduct().getSku(), i.getProduct().getName(),
                        i.getQuantity(), i.getUnitPrice()))
                .toList();

        // 2. 展開付款記錄
        List<SupportDtos.PaymentInfo> pays = o.getPayments().stream().map(this::toPaymentInfo).toList();
        Customer c = o.getCustomer();

        // 3. 組裝最終 DTO
        return new SupportDtos.OrderDetails(o.getOrderNumber(), c.getFullName(), c.getEmail(), o.getOrderDate(),
                o.getStatus().name(), o.getShippingAddress(), o.getTotalAmount(), o.getCurrency(),
                items, // 上面展開的商品明細
                pays); // 上面展開的付款記錄
    }

    /**
     * 將 Payment 實體轉換為扁平的 DTO，供 detectDuplicateCharges 和 toOrderDetails 共用。
     */
    private SupportDtos.PaymentInfo toPaymentInfo(Payment p) {
        return new SupportDtos.PaymentInfo(p.getId(), p.getAmount(), p.getCurrency(), p.getPaymentMethod(),
                p.getTransactionRef(), p.getStatus().name(), p.getChargedAt());
    }

    /**
     * 將 SupportTicket 實體轉換為扁平的 DTO，enum 欄位以 name() 字串輸出供 Agent 直接閱讀。
     */
    private SupportDtos.TicketInfo toTicketInfo(SupportTicket t) {
        return new SupportDtos.TicketInfo(t.getId(), t.getSubject(), t.getIntent().name(),
                t.getSentiment().name(), t.getStatus().name(), t.getResolution(), t.getCreatedAt());
    }
}
