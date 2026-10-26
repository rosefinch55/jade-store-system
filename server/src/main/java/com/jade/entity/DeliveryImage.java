package com.jade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 完工交付过程图（UC-17，原逗号分隔字段拆表） */
@TableName("t_delivery_image")
public class DeliveryImage {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 完工交付申请 -> t_delivery_apply */
    private Long applyId;

    /** 阶段：加工前|加工中|完工 */
    private String stage;

    /** 图片路径 */
    private String imagePath;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplyId() {
        return applyId;
    }

    public void setApplyId(Long applyId) {
        this.applyId = applyId;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }
}
