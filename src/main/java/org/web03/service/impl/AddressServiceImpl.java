package org.web03.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web03.exception.BusinessException;
import org.web03.mapper.AddressMapper;
import org.web03.pojo.Address;
import org.web03.pojo.AddressRequest;
import org.web03.service.AddressService;
import org.web03.utils.CurrentHolder;

import java.util.List;

/**
 * 地址服务实现类
 */

@Slf4j
@Service
public class AddressServiceImpl implements AddressService {

    @Autowired
    private AddressMapper addressMapper;

    //获取当前用户ID
    private String currentUserId(){
        String userId = CurrentHolder.getCurrentUserId();
        // 抛业务异常而不是 RuntimeException：前者会走 GlobalExceptionHandler 返回 code=0，
        // 后者会变成 HTTP 500 白页
        if(userId == null) throw new BusinessException("用户未登录或登录已失效");
        return userId;
    }

    /** 新增校验：必填字段一个都不能少（缺失即报错） */
    private void validateForCreate(AddressRequest addressRequest) {
        if (addressRequest == null) throw new BusinessException("地址信息不能为空");
        validateFields(addressRequest, true);
    }

    /**
     * 更新校验：只校验「本次提交的字段」。
     *
     * <p>PUT /addresses/{id} 的契约是**部分字段补丁**（前端只改一个字段时也只提交该字段），
     * 因此未提交（null）的字段不做必填校验；但一旦提交了，就必须合法且非空。
     */
    private void validateForUpdate(AddressRequest addressRequest) {
        if (addressRequest == null) throw new BusinessException("地址信息不能为空");
        validateFields(addressRequest, false);
    }

    /**
     * 字段校验
     *
     * @param required true=新增（字段缺失即报错）；false=更新（字段缺失则保持原值）
     */
    private void validateFields(AddressRequest r, boolean required) {
        // 收货人：只允许中英文字母，最长 20
        if (r.getName() != null) {
            String name = r.getName().trim();
            if (name.isEmpty()) throw new BusinessException("收货人不能为空");
            if (!name.matches("^[\\u4e00-\\u9fa5a-zA-Z]+$")) throw new BusinessException("收货人姓名格式不合法(不能使用空格、数字、标点、特殊符号)");
            if (name.length() > 20) throw new BusinessException("收货人姓名长度不能超过20");
        } else if (required) {
            throw new BusinessException("收货人不能为空");
        }

        // 手机号：11 位、1 开头
        if (r.getPhone() != null) {
            if (r.getPhone().trim().isEmpty()) throw new BusinessException("手机号不能为空");
            if (!r.getPhone().trim().matches("^1\\d{10}$")) throw new BusinessException("请输入正确的11位手机号");
        } else if (required) {
            throw new BusinessException("手机号不能为空");
        }

        // 区域
        if (r.getRegion() != null) {
            if (r.getRegion().trim().isEmpty()) throw new BusinessException("区域不能为空");
        } else if (required) {
            throw new BusinessException("区域不能为空");
        }

        // 详细地址：最长 100
        if (r.getDetail() != null) {
            String detail = r.getDetail().trim();
            if (detail.isEmpty()) throw new BusinessException("详细地址不能为空");
            if (detail.length() > 100) throw new BusinessException("详细地址长度不能超过100");
        } else if (required) {
            throw new BusinessException("详细地址不能为空");
        }

        /* 标签是可选字段：允许完全不传（null）或传空串（表示无标签），
           只有确实填了内容才校验长度。
           注意不要写成 r.getTag().trim()——tag 为 null 时会 NPE，
           表现是「新增地址不填标签就 500」。 */
        if (r.getTag() != null && r.getTag().trim().length() > 6) {
            throw new BusinessException("标签长度不能超过6");
        }
    }

    //列出所有地址
    @Override
    public List<Address> list() {
        return addressMapper.listByUserId(currentUserId());
    }

    //创建地址
    @Override
    @Transactional
    public Address create(AddressRequest addressRequest) {
        String userId = currentUserId();
        validateForCreate(addressRequest);
        Address address = new Address();
        address.setUserId(userId);
        address.setName(addressRequest.getName().trim());
        address.setPhone(addressRequest.getPhone().trim());
        address.setRegion(addressRequest.getRegion().trim());
        address.setDetail(addressRequest.getDetail().trim());
        address.setTag(addressRequest.getTag() == null ? "" : addressRequest.getTag().trim());
        address.setIsDefault(addressRequest.getIsDefault());

           /* 默认规则：
            ① 显式要求默认（isDefault=true）→ 先清空其他默认，再置当前为默认；
            ② 未显式要求，且该用户还没有任何地址 → 第一条自动设为默认（电商惯例）；
            ③ 其余情况 → 非默认。 */
        boolean wantDefault = Boolean.TRUE.equals(addressRequest.getIsDefault());
        boolean firstOne = addressMapper.countByUserId(userId) == 0;
        if (wantDefault){
            addressMapper.clearDefault(userId);
            address.setIsDefault(true);
        }else {
            address.setIsDefault(firstOne);
        }
        addressMapper.insert(address);
        log.info("新增地址: {} (id={}, 默认={})", userId, address.getId(), address.getIsDefault());
        return address;
    }

    //更新地址（部分字段补丁：只覆盖提交上来的字段）
    @Override
    @Transactional
    public Address update(Integer id, AddressRequest addressRequest) {
        String userId = currentUserId();
        validateForUpdate(addressRequest);
        Address address = addressMapper.findByIdAndUser(id, userId);
        if (address == null) throw new BusinessException("地址不存在");
        if (addressRequest.getName() != null) address.setName(addressRequest.getName().trim());
        if (addressRequest.getPhone() != null) address.setPhone(addressRequest.getPhone().trim());
        if (addressRequest.getRegion() != null) address.setRegion(addressRequest.getRegion().trim());
        if (addressRequest.getDetail() != null) address.setDetail(addressRequest.getDetail().trim());
        if (addressRequest.getTag() != null) address.setTag(addressRequest.getTag().trim());
        if (addressRequest.getIsDefault() != null) {
            if (addressRequest.getIsDefault()) addressMapper.clearDefault(userId);
            address.setIsDefault(addressRequest.getIsDefault());
        }
        Integer rows = addressMapper.update(address);
        if (rows != null && rows == 0) throw new BusinessException("地址不存在");
        log.info("更新地址: {} (id={})", userId, id);
        return address;

    }

    //删除地址
    @Override
    public void delete(Integer id) {
        String userId = currentUserId();
        addressMapper.delete(id,userId);
        log.info("删除地址: {} (id={})", userId, id);

    }

    //设置默认地址
    @Override
    @Transactional
    public void setDefault(Integer id) {
        String userId = currentUserId();
        if (id == null) throw new BusinessException("地址不存在");
        /* 先确认这条地址确实属于当前用户，再动默认标记。
           若先 clearDefault 再发现 id 无效，用户的默认地址会被清空且无法恢复
           （整账号变成"没有默认地址"），属于失败操作产生副作用。 */
        if (addressMapper.findByIdAndUser(id, userId) == null) throw new BusinessException("地址不存在");
        addressMapper.clearDefault(userId);
        Integer rows = addressMapper.setDefault(id, userId);
        if (rows != null && rows == 0) throw new BusinessException("地址不存在");
        log.info("设默认地址: {} (id={})", userId, id);

    }
}
