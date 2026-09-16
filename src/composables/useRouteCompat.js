import { computed } from 'vue';
import { useRoute } from 'vue-router';

/**
 * 兼容原版（mall-web/router.js parse）的路由对象：
 *   { name, params: string[], query: object }
 * 例：#/category/数码科技 → { name:'category', params:['数码科技'], query:{} }
 *    #/search?q=耳机    → { name:'search',    params:[],           query:{q:'耳机'} }
 */
export default function useRouteCompat() {
  const route = useRoute();
  return computed(() => {
    const params = [];
    if (route.params.catId !== undefined && route.params.catId !== '') params.push(String(route.params.catId));
    if (route.params.id !== undefined && route.params.id !== '') params.push(String(route.params.id));
    if (route.params.feature !== undefined && route.params.feature !== '') params.push(String(route.params.feature));
    if (route.params.name !== undefined && route.params.name !== '') params.push(String(route.params.name));
    return {
      name: route.name || 'home',
      params,
      query: Object.assign({}, route.query)
    };
  });
}
