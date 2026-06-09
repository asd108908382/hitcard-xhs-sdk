package space.hitcard.xhs.sdk;

import com.xiaohongshu.fls.opensdk.client.AfterSaleClient;
import com.xiaohongshu.fls.opensdk.client.BoutiqueClient;
import com.xiaohongshu.fls.opensdk.client.CommonClient;
import com.xiaohongshu.fls.opensdk.client.DataClient;
import com.xiaohongshu.fls.opensdk.client.DeliveryVoucherClient;
import com.xiaohongshu.fls.opensdk.client.ExpressClient;
import com.xiaohongshu.fls.opensdk.client.FinanceClient;
import com.xiaohongshu.fls.opensdk.client.InstantShoppingClient;
import com.xiaohongshu.fls.opensdk.client.InventoryClient;
import com.xiaohongshu.fls.opensdk.client.InvoiceClient;
import com.xiaohongshu.fls.opensdk.client.MaterialClient;
import com.xiaohongshu.fls.opensdk.client.MemberPassClient;
import com.xiaohongshu.fls.opensdk.client.OauthClient;
import com.xiaohongshu.fls.opensdk.client.OrderClient;
import com.xiaohongshu.fls.opensdk.client.PackageClient;
import com.xiaohongshu.fls.opensdk.client.ProductClient;
import com.xiaohongshu.fls.opensdk.client.SupplyOrderClient;

/**
 * Aggregates all upstream XHS OpenSDK domain clients behind one validated config.
 */
public final class XhsClientBundle {

    private final XhsClientConfig config;
    private final OauthClient oauthClient;
    private final OrderClient orderClient;
    private final PackageClient packageClient;
    private final SupplyOrderClient supplyOrderClient;
    private final AfterSaleClient afterSaleClient;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final ExpressClient expressClient;
    private final CommonClient commonClient;
    private final FinanceClient financeClient;
    private final DataClient dataClient;
    private final MemberPassClient memberPassClient;
    private final DeliveryVoucherClient deliveryVoucherClient;
    private final InstantShoppingClient instantShoppingClient;
    private final BoutiqueClient boutiqueClient;
    private final MaterialClient materialClient;
    private final InvoiceClient invoiceClient;

    private XhsClientBundle(XhsClientConfig config) {
        this.config = config;
        String url = config.getUrl();
        String appId = config.getAppId();
        String version = config.getVersion();
        String appSecret = config.getAppSecret();
        this.oauthClient = new OauthClient(url, appId, version, appSecret);
        this.orderClient = new OrderClient(url, appId, version, appSecret);
        this.packageClient = new PackageClient(url, appId, version, appSecret);
        this.supplyOrderClient = new SupplyOrderClient(url, appId, version, appSecret);
        this.afterSaleClient = new AfterSaleClient(url, appId, version, appSecret);
        this.productClient = new ProductClient(url, appId, version, appSecret);
        this.inventoryClient = new InventoryClient(url, appId, version, appSecret);
        this.expressClient = new ExpressClient(url, appId, version, appSecret);
        this.commonClient = new CommonClient(url, appId, version, appSecret);
        this.financeClient = new FinanceClient(url, appId, version, appSecret);
        this.dataClient = new DataClient(url, appId, version, appSecret);
        this.memberPassClient = new MemberPassClient(url, appId, version, appSecret);
        this.deliveryVoucherClient = new DeliveryVoucherClient(url, appId, version, appSecret);
        this.instantShoppingClient = new InstantShoppingClient(url, appId, version, appSecret);
        this.boutiqueClient = new BoutiqueClient(url, appId, version, appSecret);
        this.materialClient = new MaterialClient(url, appId, version, appSecret);
        this.invoiceClient = new InvoiceClient(url, appId, version, appSecret);
    }

    public static XhsClientBundle create(XhsClientConfig config) {
        return new XhsClientBundle(config);
    }

    public static XhsClientBundle of(String url, String appId, String appSecret) {
        return create(XhsClientConfig.of(url, appId, appSecret));
    }

    public static Builder builder() {
        return new Builder();
    }

    public XhsClientConfig config() {
        return config;
    }

    public OauthClient oauthClient() {
        return oauthClient;
    }

    public OrderClient orderClient() {
        return orderClient;
    }

    public PackageClient packageClient() {
        return packageClient;
    }

    public SupplyOrderClient supplyOrderClient() {
        return supplyOrderClient;
    }

    public AfterSaleClient afterSaleClient() {
        return afterSaleClient;
    }

    public ProductClient productClient() {
        return productClient;
    }

    public InventoryClient inventoryClient() {
        return inventoryClient;
    }

    public ExpressClient expressClient() {
        return expressClient;
    }

    public CommonClient commonClient() {
        return commonClient;
    }

    public FinanceClient financeClient() {
        return financeClient;
    }

    public DataClient dataClient() {
        return dataClient;
    }

    public MemberPassClient memberPassClient() {
        return memberPassClient;
    }

    public DeliveryVoucherClient deliveryVoucherClient() {
        return deliveryVoucherClient;
    }

    public InstantShoppingClient instantShoppingClient() {
        return instantShoppingClient;
    }

    public BoutiqueClient boutiqueClient() {
        return boutiqueClient;
    }

    public MaterialClient materialClient() {
        return materialClient;
    }

    public InvoiceClient invoiceClient() {
        return invoiceClient;
    }

    public static final class Builder {
        private final XhsClientConfig.Builder configBuilder = XhsClientConfig.builder();

        private Builder() {
        }

        public Builder url(String url) {
            configBuilder.url(url);
            return this;
        }

        public Builder appId(String appId) {
            configBuilder.appId(appId);
            return this;
        }

        public Builder appSecret(String appSecret) {
            configBuilder.appSecret(appSecret);
            return this;
        }

        public Builder version(String version) {
            configBuilder.version(version);
            return this;
        }

        public XhsClientBundle build() {
            return XhsClientBundle.create(configBuilder.build());
        }
    }
}
