package com.jade.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 货品档案（UC-01/UC-02，状态机见 07a；状态变更流水见 t_stock_log） */
@TableName("t_goods")
public class Goods {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 货品编号（条形码，一物一码，系统生成） */
    private String goodsNo;

    /** 品名 */
    private String name;

    /** 类别 -> t_category */
    private Long categoryId;

    /** 料种（自定义填写，无法穷举） */
    private String material;

    /** 产地（自定义填写，鉴定证书不判产地，由老板凭眼力判断） */
    private String origin;

    /** 器型（自定义填写，无法穷举） */
    private String shape;

    /** 产状：山料|籽料|山流水料|戈壁料 */
    private String form;

    /** 克重 */
    private BigDecimal weightG;

    /** 尺寸/圈口（如内径56mm） */
    private String size;

    /** 证书号（取最新一张证书的编号，完整证书见 t_certificate） */
    private String certNo;

    /** 图片（文件路径/URL） */
    private String image;

    /** 来源类型：采购|回收|调货|加工完工|寄售 */
    private String srcType;

    /** 来源方（供应商/商户/师傅名） */
    private String srcParty;

    /** 来源单据号（寄售时为寄售合同编号） */
    private String srcDoc;

    /** 成本（调货调入不记成本只记往来；寄售=寄售价，作结算依据） */
    private BigDecimal cost;

    /** 最低售价（成交价不得低于此价） */
    private BigDecimal minPrice;

    /** 调货价（UC-11 商户端调货价目，在柜时维护） */
    private BigDecimal transferPrice;

    /** 入库时间（系统时间） */
    private LocalDateTime inTime;

    /** 入库批次 -> t_stock_in（货品清单成员） */
    private Long stockInId;

    /** 状态：在柜|已送检|预留|已售|已订|已调出|代销中|待处理 */
    private String status;

    /** 首页精选标记：1=精确配图可上推荐位，0=仅库内可查 */
    private Integer isFeatured;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGoodsNo() {
        return goodsNo;
    }

    public void setGoodsNo(String goodsNo) {
        this.goodsNo = goodsNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getShape() {
        return shape;
    }

    public void setShape(String shape) {
        this.shape = shape;
    }

    public String getForm() {
        return form;
    }

    public void setForm(String form) {
        this.form = form;
    }

    public BigDecimal getWeightG() {
        return weightG;
    }

    public void setWeightG(BigDecimal weightG) {
        this.weightG = weightG;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getCertNo() {
        return certNo;
    }

    public void setCertNo(String certNo) {
        this.certNo = certNo;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getSrcType() {
        return srcType;
    }

    public void setSrcType(String srcType) {
        this.srcType = srcType;
    }

    public String getSrcParty() {
        return srcParty;
    }

    public void setSrcParty(String srcParty) {
        this.srcParty = srcParty;
    }

    public String getSrcDoc() {
        return srcDoc;
    }

    public void setSrcDoc(String srcDoc) {
        this.srcDoc = srcDoc;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getTransferPrice() {
        return transferPrice;
    }

    public void setTransferPrice(BigDecimal transferPrice) {
        this.transferPrice = transferPrice;
    }

    public LocalDateTime getInTime() {
        return inTime;
    }

    public void setInTime(LocalDateTime inTime) {
        this.inTime = inTime;
    }

    public Long getStockInId() {
        return stockInId;
    }

    public void setStockInId(Long stockInId) {
        this.stockInId = stockInId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getIsFeatured() {
        return isFeatured;
    }

    public void setIsFeatured(Integer isFeatured) {
        this.isFeatured = isFeatured;
    }
}
