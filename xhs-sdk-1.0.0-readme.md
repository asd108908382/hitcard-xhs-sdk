# XHS SDK 1.0.0 依赖阅读

## 结论

当前工程只在 `pom.xml` 中声明了依赖：

```xml
<dependency>
    <groupId>com.xhs</groupId>
    <artifactId>xhs-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

真实可读内容来自本机 Maven 缓存：

```text
~/.m2/repository/com/xhs/xhs-sdk/1.0.0/xhs-sdk-1.0.0.jar
~/.m2/repository/com/xhs/xhs-sdk/1.0.0/xhs-sdk-1.0.0.pom
```

这个 jar 没有源码包、没有 javadoc，POM 也没有声明传递依赖。本文基于 `jar tf`、`javap`、`jdeps` 对字节码做阅读整理。结论上，它是一个小红书开放平台 FLS OpenSDK，根包为 `com.xiaohongshu.fls.opensdk`，按业务域提供一组同步 HTTP JSON client。

## 基本结构

```text
com.xiaohongshu.fls.opensdk
├── client      # 各业务域 Client，全部继承 BaseClient
├── entity      # 请求/响应 DTO
├── util        # 签名、JSON、HTTP client、日期工具
└── exception   # SDK 自定义异常
```

主要业务 client：

| Client | 领域 |
| --- | --- |
| `OauthClient` | 授权、刷新 token |
| `OrderClient` | 订单、订单发货、订单收货信息、订单物流、报关 |
| `PackageClient` | 包裹、包裹发货、包裹收货信息、包裹物流、取消申请 |
| `SupplyOrderClient` | 供货商订单 |
| `AfterSaleClient` | 售后 |
| `ProductClient` | 商品、SPU、Item、SKU |
| `InventoryClient` | 库存、仓库 |
| `ExpressClient` | 电子面单、即时零售配送轨迹 |
| `CommonClient` | 类目、品牌、物流公司、运费模板、地址等基础数据 |
| `FinanceClient` | 结算、账单、交易/支出流水 |
| `DataClient` | 数据解密、脱敏、索引 |
| `MemberPassClient` | 会员通 |
| `DeliveryVoucherClient` | 发货凭证 |
| `BoutiqueClient` | 精品/组合商品相关 |
| `MaterialClient` | 素材 |
| `InvoiceClient` | 发票 |

注意：订单请求包名在 jar 中拼成了 `com.xiaohongshu.fls.opensdk.entity.order.Requset`，不是 `Request`；响应包名是大写 `Response`。

## 调用模型

所有业务 client 都继承：

```java
new XxxClient(String url, String appId, String version, String appSecret)
```

除 OAuth 外，业务方法基本都是：

```java
BaseResponse<T> execute(SomeRequest request, String accessToken) throws IOException;
```

OAuth 是：

```java
BaseResponse<GetAccessTokenResponse> execute(GetAccessTokenRequest request) throws IOException;
BaseResponse<RefreshTokenResponse> execute(RefreshTokenRequest request) throws IOException;
```

SDK 的实际 HTTP 行为：

| 项 | 行为 |
| --- | --- |
| 请求方式 | `POST` |
| URL | client 构造参数 `url`，每个方法都发到同一个 URL |
| Content-Type | `application/json; charset=utf-8` |
| HTTP 库 | 静态单例 `OkHttpClient` |
| 超时 | connect 5s、read 30s、write 30s |
| 重试 | `retryOnConnectionFailure(true)` |
| JSON 序列化 | 请求用 `fastjson`；响应先用 Jackson 读 `Map`，再用 `fastjson` 转目标 DTO |

`BaseRequest.addParameter(...)` 会自动填充：

| 字段 | 来源 |
| --- | --- |
| `method` | 各 client 的 `execute(...)` 内部设置 |
| `appId` | `BaseClient.appId` |
| `timestamp` | `System.currentTimeMillis() / 1000`，秒级时间戳 |
| `version` | `BaseClient.version` |
| `accessToken` | `execute(request, accessToken)` 入参，OAuth 为 `null` |
| `sign` | SDK 内部 MD5 签名 |

请求类继承 `BaseRequest`，大多只有字段、getter、setter、`equals/hashCode/toString`。`BaseRequest#setParameters()` 是空方法。

## 签名算法

签名在 `Utils.addSign(BaseRequest request, String appSecret)` 中完成。字节码确认的逻辑如下：

1. 校验 `version`、`timestamp`、`appId`、`method`、`appSecret` 非空。
2. 构造参与签名的参数列表，只包含：
   - `appId=<appId>`
   - `timestamp=<timestamp>`
   - `version=<version>`
3. 按字符串小写后的字典序排序。
4. 用 `&` 拼接成 query string。
5. 拼接待签名字符串：

```text
<method>?<sortedQueryString><appSecret>
```

示例形态：

```text
order.orderDeliver?appId=xxx&timestamp=1710000000&version=1.0secret
```

6. 对待签名字符串做 MD5，输出小写十六进制，写入 `request.sign`。

需要注意：

- `accessToken` 不参与签名。
- 业务请求体字段不参与签名。
- `method` 由 SDK 内部设置，正常使用时不需要自己 set。
- 如果要排查签名问题，应先打印最终 JSON 请求体中的 `method/appId/timestamp/version/accessToken/sign`，再按上述规则复算。

## 响应模型

统一响应包装类：

```java
BaseResponse<T>
```

字段：

| 字段 | 含义 |
| --- | --- |
| `success` | SDK 根据平台返回 `success` 判断 |
| `code` | 失败时从 `error_code` 或 `code` 取值 |
| `msg` | 失败时从 `error_msg` 或 `msg` 取值 |
| `data` | 成功时转换成对应 DTO，部分无返回体接口会填固定字符串 |

SDK 预期平台返回大致是：

```json
{
  "success": true,
  "data": {}
}
```

失败时兼容：

```json
{
  "success": false,
  "error_code": "...",
  "error_msg": "..."
}
```

或：

```json
{
  "success": false,
  "code": "...",
  "msg": "..."
}
```

## 运行依赖风险

`xhs-sdk-1.0.0.pom` 只声明自身坐标，没有任何 `<dependencies>`。但 `jdeps` 和字节码确认 jar 内部引用了这些外部包：

| 依赖包 | 用途 |
| --- | --- |
| `okhttp3` | HTTP 请求 |
| `com.alibaba.fastjson` | 请求序列化、响应 data 转 DTO |
| `com.fasterxml.jackson.databind` | 响应 JSON 转 `Map` |
| `com.fasterxml.jackson.annotation` | 部分 DTO 注解 |
| `org.apache.commons.collections` | `MapUtils.getBoolean(...)` |
| `com.google.gson` | 部分 client 字节码有引用 |

因此单独引入 `com.xhs:xhs-sdk:1.0.0` 可能会在运行时报 `ClassNotFoundException` 或 `NoClassDefFoundError`。接入工程需要显式确认这些依赖已经由现有项目提供，或者自行补齐版本。

另一个风险是响应解析：`Utils.objectFromJSONStr(...)` 捕获异常后直接返回 `null`，client 随后会对返回的 `Map` 做取值；如果平台返回非 JSON、空 body 或结构异常，可能变成空指针，而不是干净的失败响应。

## 常用调用示例

### 获取 access token

```java
OauthClient client = new OauthClient(url, appId, version, appSecret);

GetAccessTokenRequest request = new GetAccessTokenRequest();
request.setCode(authCode);

BaseResponse<GetAccessTokenResponse> response = client.execute(request);
if (!response.isSuccess()) {
    throw new IllegalStateException(response.getCode() + ":" + response.getMsg());
}

String accessToken = response.getData().getAccessToken();
```

`GetAccessTokenResponse` 主要字段：

| 字段 |
| --- |
| `accessToken` |
| `accessTokenExpiresAt` |
| `refreshToken` |
| `refreshTokenExpiresAt` |
| `sellerId` |
| `sellerName` |

### 刷新 token

```java
OauthClient client = new OauthClient(url, appId, version, appSecret);

RefreshTokenRequest request = new RefreshTokenRequest();
request.setRefreshToken(refreshToken);

BaseResponse<RefreshTokenResponse> response = client.execute(request);
```

### 查询订单列表

```java
OrderClient client = new OrderClient(url, appId, version, appSecret);

GetOrderListRequest request = new GetOrderListRequest();
request.setStartTime(startTime);
request.setEndTime(endTime);
request.setTimeType(timeType);
request.setOrderType(orderType);
request.setOrderStatus(orderStatus);
request.setPageNo(1);
request.setPageSize(50);

BaseResponse<GetOrderListResponse> response = client.execute(request, accessToken);
```

`GetOrderListRequest` 字段：

| 字段 |
| --- |
| `startTime` |
| `endTime` |
| `timeType` |
| `orderType` |
| `orderStatus` |
| `pageNo` |
| `pageSize` |

`GetOrderListResponse` 字段：

| 字段 |
| --- |
| `total` |
| `pageNo` |
| `pageSize` |
| `maxPageNo` |
| `orderList` |

订单简要信息 `OrderSimpleDetail` 包含 `orderId/orderType/orderStatus/orderAfterSalesStatus/cancelStatus/createdTime/paidTime/updateTime/deliveryTime/cancelTime/finishTime/promiseLastDeliveryTime/receiverProvinceName/receiverCityName/receiverDistrictName/customerRemark/sellerRemark/originalOrderId/logistics/orderTagList/xhsOpenId` 等字段。

### 查询订单详情

```java
GetOrderDetailRequest request = new GetOrderDetailRequest();
request.setOrderId(orderId);

BaseResponse<GetOrderDetailResponse> response =
        new OrderClient(url, appId, version, appSecret).execute(request, accessToken);
```

`GetOrderDetailResponse` 在简要信息基础上额外包含 `skuList/totalPayAmount/totalShippingFree/unpack/expressTrackingNo/expressCompanyCode/receiverName/receiverPhone/receiverAddress/openAddressId/simpleDeliveryOrderList/shopId/shopName/whcode/userId/logisticsMode/customsCode/outTradeNo` 等字段。

### 查询订单收货信息

订单详情里可能有 `openAddressId`。查询收货信息时用 `orderId + openAddressId`：

```java
GetOrderReceiverInfoRequest.OrderReceiverQuery query =
        new GetOrderReceiverInfoRequest.OrderReceiverQuery();
query.setOrderId(orderId);
query.setOpenAddressId(openAddressId);

GetOrderReceiverInfoRequest request = new GetOrderReceiverInfoRequest();
request.setReceiverQueries(Collections.singletonList(query));
request.setIsReturn(false);

BaseResponse<GetOrderReceiverInfoResponse> response =
        new OrderClient(url, appId, version, appSecret).execute(request, accessToken);
```

返回 `OrderReceiverInfo` 字段：

| 字段 |
| --- |
| `orderId` |
| `matched` |
| `receiverProvinceName` |
| `receiverCityName` |
| `receiverDistrictName` |
| `receiverTownName` |
| `receiverName` |
| `receiverPhone` |
| `receiverAddress` |
| `location` |
| `accountInfo` |

### 订单发货

```java
OrderDeliverRequest request = new OrderDeliverRequest();
request.setOrderId(orderId);
request.setExpressNo(expressNo);
request.setExpressCompanyCode(expressCompanyCode);
request.setExpressCompanyName(expressCompanyName);
request.setDeliveringTime(System.currentTimeMillis() / 1000);
request.setUnpack(false);
request.setSkuIdList(skuIdList);
request.setReturnAddressId(returnAddressId);

BaseResponse<String> response =
        new OrderClient(url, appId, version, appSecret).execute(request, accessToken);
```

SDK 内部 method 为 `order.orderDeliver`，成功时 `data` 固定填 `"发货成功"`。

### 修改订单物流

```java
ModifyOrderExpressRequest request = new ModifyOrderExpressRequest();
request.setOrderId(orderId);
request.setExpressNo(newExpressNo);
request.setExpressCompanyCode(expressCompanyCode);
request.setExpressCompanyName(expressCompanyName);
request.setDeliveryOrderIndex(deliveryOrderIndex);
request.setOldExpressNo(oldExpressNo);

BaseResponse<String> response =
        new OrderClient(url, appId, version, appSecret).execute(request, accessToken);
```

SDK 内部 method 为 `order.modifyOrderExpressInfo`。

### 包裹发货

```java
PackageDeliverRequest request = new PackageDeliverRequest();
request.setPackageId(packageId);
request.setExpressNo(expressNo);
request.setExpressCompanyCode(expressCompanyCode);
request.setExpressCompanyName(expressCompanyName);
request.setDeliveringTime(System.currentTimeMillis() / 1000);
request.setUnpack(false);
request.setItemIdList(itemIdList);

BaseResponse<String> response =
        new PackageClient(url, appId, version, appSecret).execute(request, accessToken);
```

SDK 内部 method 为 `package.packageDeliver`，成功时 `data` 固定填 `"发货成功"`。

### 查询包裹收货信息

```java
GetReceiverInfoRequest.ReceiverQuery query =
        new GetReceiverInfoRequest.ReceiverQuery();
query.setPackageId(packageId);
query.setOpenAddressId(openAddressId);

GetReceiverInfoRequest request = new GetReceiverInfoRequest();
request.setReceiverQueries(Collections.singletonList(query));
request.setIsReturn(false);

BaseResponse<GetReceiveInfoResponse> response =
        new PackageClient(url, appId, version, appSecret).execute(request, accessToken);
```

返回 `ReceiverInfo` 字段：

| 字段 |
| --- |
| `packageId` |
| `matched` |
| `receiverProvinceName` |
| `receiverCityName` |
| `receiverDistrictName` |
| `receiverTownName` |
| `receiverName` |
| `receiverPhone` |
| `receiverAddress` |

## Client 方法与平台 method 对照

### OAuth

| Java 方法入参 | 平台 method |
| --- | --- |
| `GetAccessTokenRequest` | `oauth.getAccessToken` |
| `RefreshTokenRequest` | `oauth.refreshToken` |

### 订单 OrderClient

| Java 方法入参 | 平台 method |
| --- | --- |
| `GetOrderListRequest` | `order.getOrderList` |
| `GetOrderDetailRequest` | `order.getOrderDetail` |
| `BatchBindOrderSkuIdentifyCodeInfoRequest` | `order.batchBindSkuIdentifyInfo` |
| `BondedPaymentRecordRequest` | `order.resendBondedPaymentRecord` |
| `SyncCustomsInfoRequest` | `order.syncItemCustomsInfo` |
| `GetCustomInfoRequest` | `order.getCustomsInfo` |
| `GetOrderReceiverInfoRequest` | `order.getOrderReceiverInfo` |
| `ModifyOrderExpressRequest` | `order.modifyOrderExpressInfo` |
| `OrderDeliverRequest` | `order.orderDeliver` |
| `ModifySellerMarkRequest` | `order.modifySellerMarkInfo` |
| `GetOrderTrackRequest` | `order.getOrderTracking` |
| `GetOrderDeclareRequest` | `order.getOrderDeclareInfo` |
| `GetSupportedPortListRequest` | `order.getSupportedPortList` |
| `CreateTransferBatchRequest` | `order.createTransferBatch` |
| `GetKosDataRequest` | `businessdata.getKosData` |
| `ModifyCustomsStatusRequest` | `order.modifycustomstatus` |
| `BatchApproveSubscribeOrdersRequest` | `logisticservice.batchApproveSubscribeOrders` |
| `PackageRechargeResultRequest` | `order.rechargeResult` |

### 包裹 PackageClient

| Java 方法入参 | 平台 method |
| --- | --- |
| `GetPackageListRequest` | `package.getPackageList` |
| `GetPackageDetailRequest` | `package.getPackageDetail` |
| `ResendBondedPaymentRequest` | `package.resendBondedPaymentRecord` |
| `SyncItemCustomsRequest` | `package.syncItemCustomsInfo` |
| `GetItemCustomInfoRequest` | `package.getItemCustomsInfo` |
| `GetReceiverInfoRequest` | `package.getPackageReceiverInfo` |
| `ModifyPackageExpressRequest` | `package.modifyPackageExpressInfo` |
| `PackageDeliverRequest` | `package.packageDeliver` |
| `ModifySellerMarkRequest` | `package.modifySellerMarkInfo` |
| `GetPackageTrackRequest` | `package.getPackageTracking` |
| `GetPackageDeclareRequest` | `package.getPackageDeclareInfo` |
| `GetSupportedPortListRequest` | `package.getSupportedPortList` |
| `GetCancelApplyListRequest` | `package.getCancelApplyList` |
| `AuditCancelApplyRequest` | `package.auditCancelApply` |
| `AddDeclarePortRequest` | `package.addDeclarePort` |
| `UpdateProxyPackageWeightRequest` | `package.updateProxyPackageWeight` |
| `CreateTransferBatchRequest` | `package.createTransferBatch` |

### 供货商订单 SupplyOrderClient

| Java 方法入参 | 平台 method |
| --- | --- |
| `GetSupplyOrderListRequest` | `vendor.getOrderList` |
| `SupplyOrderDeliverRequest` | `vendor.orderDeliver` |
| `ModifySupplyOrderExpressRequest` | `vendor.modifyOrderExpressInfo` |
| `GetSupplyRelationListRequest` | `vendor.getSupplyRelationList` |
| `UpdateInventoryRequest` | `vendor.updateInventory` |
| `VendorListAfterSaleInfosRequest` | `vendor.listAfterSaleInfos` |
| `VendorAuditReturnsRequest` | `vendor.auditReturns` |

### 售后 AfterSaleClient

| Java 方法入参 | 平台 method |
| --- | --- |
| `ListAfterSaleInfosRequest` | `afterSale.listAfterSaleInfos` |
| `GetAfterSaleInfoRequest` | `afterSale.getAfterSaleInfo` |
| `ListReturnRejectReasonRequest` | `afterSale.rejectReasons` |
| `GetAfterSaleListRequest` | `afterSale.listAfterSaleApi` |
| `ConfirmReceiveRequest` | `afterSale.confirmReceive` |
| `AuditReturnsRequest` | `afterSale.auditReturns` |
| `GetAfterSaleDetailRequest` | `afterSale.getAfterSaleDetail` |
| `ReturnsAbnormalRequest` | `afterSale.setReturnsAbnormal` |
| `ReceiveAndShipRequest` | `afterSale.receiveAndShip` |

### 商品 ProductClient

| Java 方法入参 | 平台 method |
| --- | --- |
| `GetBasicItemRequest` | `product.getBasicItemList` |
| `GetDetailItemRequest` | `product.getDetailItemList` |
| `GetFatSpuRequest` | `product.getSpuInfo` |
| `UpdateLogisticsPlanRequest` | `product.updateLogisticsPlan` |
| `UpdateAvailabilityRequest` | `product.updateAvailability` |
| `CreateSpuRequest` | `product.createSpu` |
| `UpdateSpuRequest` | `product.updateSpu` |
| `DeleteSpuRequest` | `product.deleteSpu` |
| `CreateItemRequest` | `product.createItem` |
| `UpdateItemRequest` | `product.updateItem` |
| `DeleteItemRequest` | `product.deleteItem` |
| `GetSpuRequest` | `product.getBasicSpu` |
| `UpdateItemPriceRequest` | `product.updateItemPrice` |
| `UpdateSpuImage` | `product.updateSpuImage` |
| `UpdateVariantImage` | `product.updateVariantImage` |
| `CreateItemV3Request` | `product.createItemV2` |
| `CreateItemAndSkuRequest` | `product.createItemAndSku` |
| `UpdateItemAndSkuRequest` | `product.updateItemAndSku` |
| `CreateSkuV3Request` | `product.createSkuV2` |
| `UpdateItemV3Request` | `product.updateItemV2` |
| `UpdateSkuV3Request` | `product.updateSkuV2` |
| `DeleteItemV3Request` | `product.deleteItemV2` |
| `DeleteSkuV3Request` | `product.deleteSkuV2` |
| `GetDetailSkuRequest` | `product.getDetailSkuList` |
| `GetItemInfoRequest` | `product.getItemInfo` |
| `SearchItemListRequest` | `product.searchItemList` |
| `UpdateSkuLogisticsPlanRequest` | `product.updateSkuLogisticsPlan` |
| `UpdateSkuPriceRequest` | `product.updateSkuPrice` |
| `UpdateSkuAvailableRequest` | `product.updateSkuAvailable` |
| `UpdateItemImageRequest` | `product.updateItemImage` |

### 库存 InventoryClient

| Java 方法入参 | 平台 method |
| --- | --- |
| `GetItemStockRequest` | `inventory.getItemStock` |
| `SyncItemStockRequest` | `inventory.syncItemStock` |
| `IncItemStockRequest` | `inventory.incItemStock` |
| `GetSkuStockRequest` | `inventory.getSkuStock` |
| `SyncSkuStockRequest` | `inventory.syncSkuStock` |
| `IncSkuStockRequest` | `inventory.incSkuStock` |
| `GetSkuStockV2Request` | `inventory.getSkuStockV2` |
| `SyncSkuStockV2Request` | `inventory.syncSkuStockV2` |
| `CreateWarehouseRequest` | `warehouse.create` |
| `UpdateWarehouseRequest` | `warehouse.update` |
| `ListWarehouseRequest` | `warehouse.list` |
| `GetWarehouseRequest` | `warehouse.info` |
| `SetWarehouseCoverageRequest` | `warehouse.setCoverage` |
| `SetWarehousePriorityRequest` | `warehouse.setPriority` |

### 电子面单 ExpressClient

| Java 方法入参 | 平台 method |
| --- | --- |
| `ElectronicBillSubscribesQueryRequest` | `express.queryEbillSubscribes` |
| `ElectronicBillTemplatesQueryRequest` | `express.queryEbillTemplates` |
| `ElectronicBillOrderQueryRequest` | `express.queryEbillOrder` |
| `ElectronicBillOrdersCreateRequest` | `express.createEbillOrders` |
| `ElectronicBillOrderUpdateRequest` | `express.updateEbillOrder` |
| `ElectronicBillOrderCancelRequest` | `express.cancelEbillOrder` |
| `ElectronicBillServiceQueryRequest` | `express.queryEbillServices` |

### 基础数据 CommonClient

| Java 方法入参 | 平台 method |
| --- | --- |
| `GetCategoriesRequest` | `common.getCategories` |
| `GetAttributeValuesRequest` | `common.getAttributeValues` |
| `GetAttributeListRequest` | `common.getAttributeLists` |
| `GetVariationsRequest` | `common.getVariations` |
| `GetExpressCompanyListRequest` | `common.getExpressCompanyList` |
| `GetLogisticsListRequest` | `common.getLogisticsList` |
| `GetCarriageTemplateListRequest` | `common.getCarriageTemplateList` |
| `GetCarriageTemplateRequest` | `common.getCarriageTemplate` |
| `ListAccountTemplateRequest` | `common.listAccountTemplate` |
| `GetBrandRequest` | `common.brandSearch` |
| `GetLogisticsModeRequest` | `common.logisticsMode` |
| `GetSellerKeyInfoRequest` | `common.getSellerKeyInfo` |
| `GetNestZoneRequest` | `common.getNestZone` |
| `CategoryMatchRequest` | `common.categoryMatch` |
| `BatchGetDeliveryRuleRequest` | `common.getDeliveryRule` |
| `GetSellerAddressRecordBySellerIdRequest` | `common.getAddressRecord` |
| `GetZonesRequest` | `common.getZones` |
| `CategoryMatchV2Request` | `common.categoryMatchV2` |
| `CheckForbiddenKeywordRequest` | `common.checkForbiddenKeyword` |

### 其他 Client

| Client | Java 方法入参 | 平台 method |
| --- | --- | --- |
| `FinanceClient` | `QueryCpsSettleRequest` | `bill.queryCpsSettle` |
| `FinanceClient` | `DownloadStatementRequest` | `bill.downloadStatement` |
| `FinanceClient` | `QuerySellerAccountRecordsRequest` | `finance.querySellerAccountRecords` |
| `FinanceClient` | `PageQueryTransactionRequest` | `finance.pageQueryTransaction` |
| `FinanceClient` | `PageQueryExpenseRequest` | `finance.pageQueryExpense` |
| `DataClient` | `BatchDecryptRequest` | `data.batchDecrypt` |
| `DataClient` | `BatchDesensitiseRequest` | `data.batchDesensitise` |
| `DataClient` | `BatchIndexRequest` | `data.batchIndex` |
| `MemberPassClient` | `GetMemberInfoRequest` | `memberpass.members.query` |
| `MemberPassClient` | `UpdateMemberInfoRequest` | `memberpass.member.modify` |
| `DeliveryVoucherClient` | `BindOrderDeliveryVoucherRequest` | `order.bindDeliveryVoucher` |
| `DeliveryVoucherClient` | `DeliveryVoucherActionRequest` | `order.deliveryVoucherAction` |
| `InstantShoppingClient` | `UpdateInstantShoppingTrackRequest` | `express.instantshopping.updateInstantShoppingTrack` |
| `InstantShoppingClient` | `UpdateRiderLocationRequest` | `express.instantshopping.updateRiderLocation` |
| `BoutiqueClient` | `CreateBoutiqueItemRequest` | `boutique.createBoutiqueItem` |
| `BoutiqueClient` | `UpdateBoutiqueItemRequest` | `boutique.updateBoutiqueItem` |
| `BoutiqueClient` | `CreateItemRequest` | `boutique.createBoutiqueItemV2` |
| `BoutiqueClient` | `CreateBoutiqueSkuRequest` | `boutique.createBoutiqueSku` |
| `BoutiqueClient` | `UpdateBoutiqueSkuRequest` | `boutique.updateBoutiqueSku` |
| `BoutiqueClient` | `FlsCreateSkuRequest` | `boutique.createBoutiqueSkuV2` |
| `MaterialClient` | `UploadMaterialInfoRequest` | `material.uploadMaterial` |
| `MaterialClient` | `UpdateMaterialInfoRequest` | `material.updateMaterial` |
| `MaterialClient` | `DeleteMaterialInfoRequest` | `material.deleteMaterial` |
| `MaterialClient` | `QueryMaterialInfoRequest` | `material.queryMaterial` |
| `InvoiceClient` | `GetInvoiceListRequest` | `invoice.getInvoiceList` |
| `InvoiceClient` | `ConfirmInvoiceRequest` | `invoice.confirmInvoice` |
| `InvoiceClient` | `ReverseInvoiceRequest` | `invoice.reverseInvoice` |

## 接入建议

1. 先确认平台网关 URL。SDK 每个 client 都只知道一个 `url`，不按业务 path 拼地址。
2. 不要手动设置 `method/sign/timestamp`，让 SDK 在 `execute(...)` 内部生成。
3. 显式管理 `accessToken` 生命周期，OAuth 响应包含 access/refresh token 及过期时间。
4. 补齐并锁定外部运行依赖版本，尤其是 `okhttp3`、`fastjson`、`jackson`、`commons-collections`。
5. 对 `execute(...)` 做外层异常兜底，至少捕获 `IOException`、空指针、JSON 解析异常造成的运行时异常。
6. `BaseClient#toString()` 会输出 `appSecret`，不要在日志中直接打印 client 对象。
7. 发货、改物流等写接口成功时可能只返回固定中文字符串，不要依赖 `data` 中有平台业务对象。
8. 具体枚举值含义，例如 `orderStatus/timeType/orderType/packageStatus`，jar 里没有足够语义说明，需要以小红书开放平台文档为准。

## 当前工程的封装结果

这个工程现在不再只是一个空壳依赖，而是一个可直接复用的客户端包：

| 类 | 作用 |
| --- | --- |
| `space.hitcard.xhs.sdk.XhsClientConfig` | 统一配置对象，封装 `url/appId/appSecret/version` |
| `space.hitcard.xhs.sdk.XhsClientBundle` | 推荐主入口，负责聚合全部业务域 client |
| `space.hitcard.xhs.sdk.oauth.XhsOauthTokenManager` | OAuth token 交换、读取、刷新协调 |
| `space.hitcard.xhs.sdk.oauth.XhsOauthManagerFactory` | OAuth manager / Redis store 工厂 |
| `space.hitcard.xhs.sdk.oauth.RedisXhsOauthTokenStore` | Redis 共享 token 存储，适合集群 |
| `space.hitcard.xhs.sdk.oauth.MapBackedXhsOauthTokenStore` | 本地内存存储，适合单机测试 |

最简用法：

```java
XhsClientBundle clients = XhsClientBundle.of(
        "https://your-xhs-gateway",
        "your-app-id",
        "your-app-secret"
);

clients.oauthClient();
clients.orderClient();
clients.packageClient();
clients.productClient();
clients.inventoryClient();
```

Builder 写法：

```java
XhsClientBundle clients = XhsClientBundle.builder()
        .url("https://your-xhs-gateway")
        .appId("your-app-id")
        .appSecret("your-app-secret")
        .version("1.0")
        .build();
```

## 集群 OAuth

如果是多节点部署，不建议每个节点各自拿着本地 token 自己刷新。当前封装已经补了集群可见的 OAuth 管理层，推荐形态是：

1. 所有节点共享同一个 Redis。
2. 用业务维度生成稳定的 `tokenKey`，例如店铺、seller、租户维度。
3. Redis key namespace 默认会自动拼接 `appId`，避免多个 XHS 应用实例互相覆盖。
4. 所有节点都通过同一个 `XhsOauthTokenManager` 读 token。
5. token 即将过期时，由 manager 触发刷新，并通过 Redis 中的版本号做乐观更新，避免多个节点互相覆盖。

最简用法：

```java
JedisPool jedisPool = new JedisPool("redis-host", 6379);

XhsClientBundle clients = XhsClientBundle.of(
        "https://your-xhs-gateway",
        "your-app-id",
        "your-app-secret"
);

XhsOauthTokenManager tokenManager = XhsOauthManagerFactory.redis(
        clients.oauthClient(),
        jedisPool,
        clients.config().getAppId()
);

XhsOauthTokenRecord token = tokenManager.exchangeCode("shop:123", authCode);
String accessToken = tokenManager.requireAccessToken("shop:123");
```

默认 Redis key 形态：

```text
xhs:oauth:token:<appId>:<tokenKey>
```

如果需要自定义 Redis key 前缀：

```java
XhsOauthTokenManager tokenManager =
        XhsOauthManagerFactory.redis(
                clients.oauthClient(),
                jedisPool,
                "biz:xhs:oauth:",
                clients.config().getAppId()
        );
```

自定义前缀后 key 形态：

```text
biz:xhs:oauth:<appId>:<tokenKey>
```

如果你自己直接 new store，推荐显式传入 `appId`：

```java
RedisXhsOauthTokenStore store =
        new RedisXhsOauthTokenStore(jedisPool, "biz:xhs:oauth:", clients.config().getAppId());
```

当前设计重点：

- token 状态在 Redis 里共享，所有节点可见。
- 访问 token 时会自动判断是否需要提前刷新。
- 刷新时用版本号做 compare-and-set，减少并发刷新覆盖。
- 如果当前节点刷新失败，但其他节点已经成功刷新并写回 Redis，会优先回退读取最新 token。

这个实现解决的是“集群间 token 可见性”和“刷新竞争”问题，不负责扫码/回调链路本身，也不做分布式长锁。

## 产物

当前构建会产出三类文件：

| 文件 | 用途 |
| --- | --- |
| `target/hitcard-xhs-sdk-1.0.0.jar` | 轻量业务封装 jar，自身不含第三方依赖 |
| `target/hitcard-xhs-sdk-1.0.0-all.jar` | fat jar，已打入 `xhs-sdk` 及运行依赖，适合集群直接投放 |
| `target/hitcard-xhs-sdk-1.0.0-sources.jar` | 源码包 |

如果集群环境本身没有统一依赖管理，优先使用 `-all.jar`。
