package com.example.processor.dto;

public class TransactionStatusMessage {
    private String messageId;
    private String receiptHandle;
    private String md5OfBody;
    private TransactionStatusEvent transactionStatusEvent;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getReceiptHandle() {
        return receiptHandle;
    }

    public void setReceiptHandle(String receiptHandle) {
        this.receiptHandle = receiptHandle;
    }

    public String getMd5OfBody() {
        return md5OfBody;
    }

    public void setMd5OfBody(String md5OfBody) {
        this.md5OfBody = md5OfBody;
    }

    public TransactionStatusEvent getTransactionStatusEvent() {
        return transactionStatusEvent;
    }

    public void setTransactionStatusEvent(TransactionStatusEvent transactionStatusEvent) {
        this.transactionStatusEvent = transactionStatusEvent;
    }
}
