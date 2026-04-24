package com.example.vote.util;

import com.example.vote.constant.CommonConst;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;

@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoUtil {

    private boolean result;

    private String code;

    private String msg;

    private String date;

    private Object data;

    /**
     * Gets default false bo.
     *
     * @return the default false bo
     */
    public static BoUtil getDefaultFalseBo() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        Timestamp now = new Timestamp(System.currentTimeMillis());

        String time = df.format(now);

        return BoUtil.builder().result(false).code("").date(time).msg("failed!").data(null).build();
    }

    /**
     * Gets default true bo.
     *
     * @return the default true bo
     */
    public static BoUtil getDefaultTrueBo() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        Timestamp now = new Timestamp(System.currentTimeMillis());

        String time = df.format(now);

        return BoUtil.builder().result(true).code(CommonConst.SUCCESS).date(time).msg(CommonConst.SUCCESS_MSG).data(null)
                .build();
    }

    @Override
    public String toString() {
        ObjectMapper objectMapper = new ObjectMapper();
        String s = "";
        try {
            s = objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            log.error("", e);
        }
        return s;
    }
}
