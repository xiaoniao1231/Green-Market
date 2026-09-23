package org.web03.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.web03.exception.BusinessException;
import org.web03.mapper.EmpMapper;
import org.web03.mapper.ShopMapper;
import org.web03.pojo.Shop.Shop;
import org.web03.pojo.Shop.ShopFollowResult;
import org.web03.pojo.Shop.ShopInformation;
import org.web03.pojo.Shop.ShopRequest;
import org.web03.service.ShopService;
import org.web03.utils.AliyunOSSOperator;
import org.web03.utils.CurrentHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 店铺业务逻辑实现类
 */


@Slf4j
@Service
public class ShopServiceImpl implements ShopService {

    @Autowired
    private EmpMapper empMapper;
    @Autowired
    private ShopMapper shopMapper;
    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    //将Shop对象转换为ShopInformation对象
    private ShopInformation toVo(Shop shop){
        return new ShopInformation(shop.getShopId(),shop.getName(),shop.getOwnerUserId(),
                empMapper.findNicknameByUserId(shop.getOwnerUserId()),shop.getAvatar(),shop.getIntro(),
                shop.getScore(),shop.getFans(),shop.getFounded() == null?null:shop.getFounded().toString());
    }

    //获取当前登录用户
    private String currentOwner(){
        String userId = CurrentHolder.getCurrentUserId();
        if(!StringUtils.hasLength(userId)) throw new BusinessException("未登录或登录已失效");
        return userId;
    }

    //创建店铺
    @Override
    @Transactional
    public ShopInformation createShop(ShopRequest shopRequest) {
        String ownerId = currentOwner();
        String name = shopRequest.getName() == null ? null : shopRequest.getName();
        if (!StringUtils.hasLength(name)) throw new BusinessException("店铺名称不能为空");
        /*name.trim(); 去除名称前后的空格*/
        if(name.trim().length() < 2 || name.trim().length()>20)
            throw new RuntimeException("店铺名称长度必须在2到20个字符之间");
        if (shopMapper.countByName(name, "") > 0) throw new BusinessException("店铺名称已被占用");
        String intro = shopRequest.getIntro() == null ? null : shopRequest.getIntro();
        if (intro != null && intro.length() > 120) throw new BusinessException("店铺简介长度不能超过120个字符");

        // 店铺标识后端生成（shop- + 随机 10 位，保证唯一且不依赖用户输入）
        String shopId = "shop-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        Shop shop = new Shop();
        shop.setShopId(shopId);
        shop.setName(name);
        shop.setOwnerUserId(ownerId);
        shop.setScore(new BigDecimal("5.00"));
        shop.setAvatar(shopRequest.getAvatar());
        shop.setFans(0);
        shop.setIntro(intro);
        shop.setFounded(LocalDate.now());
        shopMapper.insert(shop);
        shopMapper.bindUserShop(shopId, ownerId);
        log.info("开店成功: {} → {} ({})", ownerId, name, shopId);
        return toVo(shop);
    }

    //上传店铺头像
    @Override
    public Map<String, Object> uploadAvatar(MultipartFile file) {
        if (file.getSize() > 10 * 1024 * 1024) throw new BusinessException("图片不能超过 10MB");
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new BusinessException("请选择 jpg / png 等图片文件");
        }
        try {
            String url = aliyunOSSOperator.upload(file.getBytes(), Objects.requireNonNull(file.getOriginalFilename()));
            Map<String, Object> data = new HashMap<>();
            data.put("url", url);
            return data;
        } catch (Exception e) {
            log.error("店铺头像上传失败", e);
            throw new BusinessException("头像上传失败：" + e.getMessage());
        }
    }

    //获取我的店铺
    @Override
    public ShopInformation getMyShop() {
        Shop shop = shopMapper.findByOwnerUserId(currentOwner());
        if(shop == null)
            throw new BusinessException("当前账号未开店");
        return toVo(shop);
    }

    //更新店铺信息
    @Override
    public ShopInformation updateShop(ShopRequest shopRequest) {
        Shop shop = shopMapper.findByOwnerUserId(currentOwner());
        if (shop == null) throw new BusinessException("当前账号未开店");
        if (shopRequest == null) throw new BusinessException("请至少提交一项修改");
        // 更新名称
        if (shopRequest.getName() != null){
            String name = shopRequest.getName().trim();
            if (name.length() < 2 || name.length() > 20)
                throw new BusinessException("店铺名称长度必须在2到20个字符之间");
            if (shopMapper.countByName(name, shop.getShopId()) > 0)
                throw new BusinessException("店铺名称已被占用");
            shop.setName(name);
        }
        // 更新简介
        if (shopRequest.getIntro() != null){
            String intro = shopRequest.getIntro().trim();
            if (intro.length() > 120)
                throw new BusinessException("店铺简介长度不能超过120个字符");
            shop.setIntro(intro);
        }
        // 更新头像
        if (shopRequest.getAvatar() != null) shop.setAvatar(shopRequest.getAvatar());

        shopMapper.updateProfile(shop);
        return toVo(shop);
    }

    //获取公开店铺信息
    @Override
    public ShopInformation getPublicShop(String shopId) {
        if (!StringUtils.hasLength(shopId)) throw new BusinessException("店铺不存在");
        Shop shop = shopMapper.findById(shopId);
        if (shop == null) throw new BusinessException("店铺不存在");
        ShopInformation info = toVo(shop);
        /* 关注态必须由服务端下发：本地 shopFavs 只是镜像，换设备 / 清缓存 / 新标签页后
           会丢失，导致已关注的店铺显示成「未关注」，而粉丝数却不为 0（前端无法自洽）。 */
        String userId = CurrentHolder.getCurrentUserId();
        info.setFollowed(StringUtils.hasLength(userId)
                && shopMapper.countFollow(userId, shop.getShopId()) > 0);
        return info;
    }

    //关注店铺
    @Override
    @Transactional
    public ShopFollowResult follow(String shopId) {
        String userId = currentOwner();
        Shop shop = requireShop(shopId);
        //已关注（或并发重复点击）时影响 0 行，此时不再给粉丝数 +1，保证接口幂等 —— 反复点关注不会把粉丝数越点越多
        int rows = shopMapper.insertFollow(userId, shop.getShopId());
        if (rows > 0) {
            shopMapper.increaseFans(shop.getShopId());
            log.info("{} 关注店铺 {} (粉丝数 +1)", userId, shop.getShopId());
        } else {
            log.info("{} 已关注店铺 {}, 粉丝数不变", userId, shop.getShopId());
        }
        return new ShopFollowResult(shop.getShopId(), latestFans(shop.getShopId()), true);
    }

    //取消关注店铺
    @Override
    @Transactional
    public ShopFollowResult unfollow(String shopId) {
        String userId = currentOwner();
        Shop shop = requireShop(shopId);
        int rows = shopMapper.deleteFollow(userId, shop.getShopId());
        if (rows > 0) {
            shopMapper.decreaseFans(shop.getShopId());
            log.info("{} 取消关注店铺 {} (粉丝数 -1)", userId, shop.getShopId());
        } else {
            log.info("{} 未关注店铺 {}, 粉丝数不变", userId, shop.getShopId());
        }
        return new ShopFollowResult(shop.getShopId(), latestFans(shop.getShopId()), false);
    }

    //店铺存在性校验（关注 / 取关共用）
    private Shop requireShop(String shopId) {
        if (!StringUtils.hasLength(shopId)) throw new BusinessException("店铺不存在");
        Shop shop = shopMapper.findById(shopId);
        if (shop == null) throw new BusinessException("店铺不存在");
        return shop;
    }

    //读取操作后的最新粉丝数，回显给前端（店铺刚被删除时回退 0）
    private Integer latestFans(String shopId) {
        Shop latest = shopMapper.findById(shopId);
        return (latest == null || latest.getFans() == null) ? 0 : latest.getFans();
    }
}
