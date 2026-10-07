package edu.cit.soldano.supplier;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

class LegacyXmlPayloads {

    @JacksonXmlRootElement(localName = "AuthRequest")
    record AuthRequest(
        @JacksonXmlProperty(localName = "ClientId") String clientId,
        @JacksonXmlProperty(localName = "ApiKey") String apiKey
    ) {}

    @JacksonXmlRootElement(localName = "AuthResponse")
    record AuthResponse(
        @JacksonXmlProperty(localName = "SessionToken") String sessionToken,
        @JacksonXmlProperty(localName = "IssuedAt") String issuedAt
    ) {}

    @JacksonXmlRootElement(localName = "PurchaseOrder")
    record PoRequest(
        @JacksonXmlProperty(localName = "SupplierSku") String supplierSku,
        @JacksonXmlProperty(localName = "Qty") int qty,
        @JacksonXmlProperty(localName = "BuyerRef") String buyerRef
    ) {}

    @JacksonXmlRootElement(localName = "PurchaseOrderAck")
    record PoResponse(
        @JacksonXmlProperty(localName = "PoNumber") String poNumber,
        @JacksonXmlProperty(localName = "StatusCode") String statusCode,
        @JacksonXmlProperty(localName = "SupplierSku") String supplierSku,
        @JacksonXmlProperty(localName = "Qty") Integer qty,
        @JacksonXmlProperty(localName = "Uom") String uom,
        @JacksonXmlProperty(localName = "BuyerRef") String buyerRef,
        @JacksonXmlProperty(localName = "CreatedAt") String createdAt
    ) {}

    @JacksonXmlRootElement(localName = "OrderStatus")
    record StatusResponse(
        @JacksonXmlProperty(localName = "PoNumber") String poNumber,
        @JacksonXmlProperty(localName = "StatusCode") String statusCode,
        @JacksonXmlProperty(localName = "Status") String status,
        @JacksonXmlProperty(localName = "SupplierSku") String supplierSku,
        @JacksonXmlProperty(localName = "Qty") Integer qty,
        @JacksonXmlProperty(localName = "Uom") String uom,
        @JacksonXmlProperty(localName = "BuyerRef") String buyerRef,
        @JacksonXmlProperty(localName = "CheckedAt") String checkedAt
    ) {}
}