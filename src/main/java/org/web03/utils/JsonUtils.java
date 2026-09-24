package org.web03.utils;


import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.web03.exception.BusinessException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

/**
 * 工具类
 */

@Slf4j
public class JsonUtils {


    /** ObjectMapper实例 */
    private static final ObjectMapper OM = new ObjectMapper();

    /** 构建商品展示图：入参是商品 skus JSON（与 ProductServiceImpl / CartServiceImpl 同口径），
        取第一个带图的款式值作为展示图 */
    public static Map<String, Object> buildArt(String skusJson) {
        return buildArtFromImg(firstSkuImg(skusJson));
    }

    /** 由「已知的图片地址」构建展示图：order_items.art_img 列存的就是一张图片地址，
        不能再交给 firstSkuImg 当 skus JSON 解析（否则 https 地址解析失败，
        订单条目永远只显示「表情 + 渐变」占位图，日志会打印 skus解析失败） */
    public static Map<String, Object> buildArtFromImg(String img) {
        Map<String, Object> artMap = new HashMap<>();
        if (StringUtils.hasLength(img)) {
            artMap.put("img", img);
        } else {
            artMap.put("e", "🛍️");
            artMap.put("g", Arrays.asList("#e8e8e8", "#f5f5f5"));
        }
        return artMap;
    }

    /** 获取第一个SKU的图片*/
    public static String firstSkuImg(String skusJson) {
        if (!StringUtils.hasLength(skusJson)) return null;
        try {
            List<Map<String, Object>> skus = OM.readValue(skusJson, new TypeReference<>() {
            });
            for (Map<String, Object> g : skus) {
                Object valuesObj = g.get("values");
                if (!(valuesObj instanceof List)) continue;
                for (Object item : (List<?>) valuesObj) {
                    if (!(item instanceof Map)) continue;
                    Object img = ((Map<?, ?>) item).get("img");
                    if (img != null && StringUtils.hasLength(String.valueOf(img))) {
                        return String.valueOf(img);
                    }
                }
            }
        } catch (Exception e) {
            log.error("skus解析失败，skus:{}", skusJson, e);
        }
        return null;
    }

    /**
     * 按下单时选中的规格文本取款式图（与前端 skuImgFromProduct 同口径）：
     * skuText 形如「10000mAh / 云朵白」，按 ' / ' 拆开依次与每个规格分组的取值比对，
     * 命中且该值带 img 时返回对应款式图。这样「用户买的是哪个款式，订单里就显示哪个款式的图」，
     * 而不是笼统地取商品第一张款式图。
     * 规格文本对不上（商品后来改过款式 / 快速加购的「默认」）时回退第一张带图的款式图。
     */
    public static String skuImg(String skusJson, String skuText) {
        if (!StringUtils.hasLength(skusJson)) return null;
        if (!StringUtils.hasLength(skuText)) return firstSkuImg(skusJson);
        try {
            List<Map<String, Object>> skus = OM.readValue(skusJson, new TypeReference<>() {
            });
            String[] parts = skuText.split("/");
            for (int gi = 0; gi < skus.size() && gi < parts.length; gi++) {
                Object valuesObj = skus.get(gi).get("values");
                if (!(valuesObj instanceof List)) continue;
                String want = parts[gi].trim();
                for (Object item : (List<?>) valuesObj) {
                    if (!(item instanceof Map)) continue;
                    Map<?, ?> value = (Map<?, ?>) item;
                    Object text = value.get("v");
                    Object img = value.get("img");
                    if (text != null && want.equals(String.valueOf(text).trim())
                            && img != null && StringUtils.hasLength(String.valueOf(img))) {
                        return String.valueOf(img);
                    }
                }
            }
        } catch (Exception e) {
            log.error("skus解析失败，skus:{}", skusJson, e);
        }
        return firstSkuImg(skusJson);
    }

    /** obj → JSON 字符串，异常则抛业务异常 */
    public static String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return OM.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BusinessException("数据格式错误");
        }
    }

    /** JSON 字符串 → Map（空 / 解析失败返回 null） */
    public static Map<String, Object> parseMap(String json) {
        if (!StringUtils.hasLength(json)) return null;
        try {
            return OM.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("json解析失败，json:{}", json, e);
            return null;
        }
    }

    /** JSON 字符串 → List<Map>（空 / 解析失败返回空列表） */
    public static List<Map<String, Object>> parseList(String json) {
        if (!StringUtils.hasLength(json)) return new ArrayList<>();
        try {
            return OM.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("json解析失败，json:{}", json, e);
            return new ArrayList<>();
        }
    }

    /** JSON 字符串 → List<List<String>>，异常则返回空列表 */
    public static List<List<String>> parseParams(String json) {
        if (!StringUtils.hasLength(json)) return new ArrayList<>();
        try {
            return OM.readValue(json, new TypeReference<List<List<String>>>() {});
        } catch (Exception e) {
            log.error("json解析失败，json:{}", json, e);
            return new ArrayList<>();
        }
    }
}
