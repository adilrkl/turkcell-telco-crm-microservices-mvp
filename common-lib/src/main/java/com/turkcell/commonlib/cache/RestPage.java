package com.turkcell.commonlib.cache;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * Jackson ile serialize/deserialize edilebilen {@link Page} implementasyonu.
 * Spring'in {@code PageImpl}'i Jackson tarafindan deserialize EDILEMEZ (no-arg ctor yok),
 * bu yuzden Redis cache'ine yazilan sayfali sonuclar okunurken patlar. {@code RestPage}
 * bir {@code @JsonCreator} ctor'u ile bu sorunu cozer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RestPage<T> extends PageImpl<T> {

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public RestPage(@JsonProperty("content") List<T> content,
                    @JsonProperty("number") int number,
                    @JsonProperty("size") int size,
                    @JsonProperty("totalElements") long totalElements) {
        super(content, PageRequest.of(size <= 0 ? 0 : number, size <= 0 ? 1 : size), totalElements);
    }

    public RestPage(Page<T> page) {
        super(page.getContent(), page.getPageable(), page.getTotalElements());
    }

    public RestPage(List<T> content) {
        super(content);
    }
}
