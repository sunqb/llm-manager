package com.example.llmmanager.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@TableName("api_keys")
public class ApiKey {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name; // e.g. "Client A", "Mobile App"
    
    private String token;

    private boolean active = true;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    private LocalDateTime expiresAt; // Optional

    public void generateToken() {
        if (token == null) {
            token = "sk-" + UUID.randomUUID().toString().replace("-", "");
        }
    }
}
