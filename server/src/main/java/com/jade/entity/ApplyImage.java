package com.jade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 出让申请图片（UC-14，原逗号分隔字段拆表） */
@TableName("t_apply_image")
public class ApplyImage {
    /** 代理键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 出让申请 -> t_transfer_apply */
    private Long applyId;

    /** 显示顺序 */
    private Integer seq;

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

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }
}
