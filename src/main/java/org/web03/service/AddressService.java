package org.web03.service;

import org.web03.pojo.Address;
import org.web03.pojo.AddressRequest;

import java.util.List;

public interface AddressService {
    List<Address> list();

    Address create(AddressRequest addressRequest);

    Address update(Integer id, AddressRequest addressRequest);

    void delete(Integer id);

    void setDefault(Integer id);
}
