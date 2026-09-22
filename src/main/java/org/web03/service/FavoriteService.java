package org.web03.service;

import org.web03.pojo.Favorite.FavoriteAddRequest;
import org.web03.pojo.Favorite.FavoriteFanHui;

public interface FavoriteService {
    FavoriteFanHui list(FavoriteFanHui favoriteFanHui);

    void add(FavoriteAddRequest favoriteAddRequest);

    void remove(Integer productId);

    void clear();
}
