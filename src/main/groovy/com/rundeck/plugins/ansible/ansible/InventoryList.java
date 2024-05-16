package com.rundeck.plugins.ansible.ansible;

import com.dtolabs.rundeck.core.resources.ResourceModelSourceException;
import lombok.Data;

import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;

@Data
public class InventoryList {

    public static final String ALL = "all";
    public static final String CHILDREN = "children";
    public static final String HOSTS = "hosts";
    public static final String ERROR_MISSING_FIELD = "Error: Missing tag '%s'";

    private Map<String, Object> all;

    public static Map<String, Object> getField(Map<String, Object> tag, final String field) throws ResourceModelSourceException {
        return getMap( Optional.ofNullable(tag.get(field))
                .orElseThrow(() -> new ResourceModelSourceException(format(ERROR_MISSING_FIELD, field))));
    }

    @SuppressWarnings("unchecked")
    public static <T> T getMap(Object obj) {
        return (T) obj;
    }
}
