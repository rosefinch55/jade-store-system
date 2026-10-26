package com.jade.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 货品档案（UC-01/UC-02，状态机见 07a；状态变更流水见 t_stock_log） */
@TableName("t_certificate")
public class Certificate {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 证书编号 */
    private String certNo;

    /** 鉴定机构 -> t_cert_org */
    private Long orgId;

    /** 送检记录 -> t_inspection（证书由送检出具，随货品带入的证书可空） */
    private Long inspId;

    /** 货品 -> t_goods（证书挂到货品） */
    private Long goodsId;

    /** 检测结论 */
    private String conclusion;

    /** 签发日期 */
    private LocalDate issueDate;

    /** 证书二维码（机构印制，扫码可查） */
    private String certQrcode;

    /** 创建时间 */
    private LocalDateTime createdAt;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCertNo() {
        return certNo;
    }

    public void setCertNo(String certNo) {
        this.certNo = certNo;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public Long getInspId() {
        return inspId;
    }

    public void setInspId(Long inspId) {
        this.inspId = inspId;
    }

    public Long getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Long goodsId) {
        this.goodsId = goodsId;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public String getCertQrcode() {
        return certQrcode;
    }

    public void setCertQrcode(String certQrcode) {
        this.certQrcode = certQrcode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
