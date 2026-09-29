package org.web03.service;

import org.web03.pojo.Footprint.Footprint;
import org.web03.pojo.Footprint.FootprintListResult;

public interface FootprintService {
    FootprintListResult list(Integer page, Integer size);

    void record(Footprint request);
}
