package com.example.pstarchive.encoding;

import com.pff.PSTObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

public class PstRawPropertyAccessor {
    private final Field itemsField;
    private final Field localDescriptorItemsField;
    private final Field itemDataField;
    private final Field itemExternalField;
    private final Field itemReferenceField;
    private final Field itemValueTypeField;

    public PstRawPropertyAccessor() {
        try {
            itemsField = field(PSTObject.class, "items");
            localDescriptorItemsField = field(PSTObject.class, "localDescriptorItems");
            Class<?> tableItemClass = Class.forName("com.pff.PSTTableItem");
            itemDataField = field(tableItemClass, "data");
            itemExternalField = field(tableItemClass, "isExternalValueReference");
            itemReferenceField = field(tableItemClass, "entryValueReference");
            itemValueTypeField = field(tableItemClass, "entryValueType");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to initialize java-libpst raw property reflection", e);
        }
    }

    public Optional<byte[]> rawBytes(PSTObject object, int propertyId) {
        try {
            Object item = item(object, propertyId);
            if (item == null) {
                return Optional.empty();
            }
            boolean external = itemExternalField.getBoolean(item);
            if (!external) {
                return bytesFromItemData(item);
            }
            Object descriptorItem = descriptorItem(object, itemReferenceField.getInt(item));
            if (descriptorItem == null) {
                return Optional.empty();
            }
            Method getData = descriptorItem.getClass().getDeclaredMethod("getData");
            getData.setAccessible(true);
            Object data = getData.invoke(descriptorItem);
            return data instanceof byte[] bytes && bytes.length > 0 ? Optional.of(bytes) : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public Optional<Integer> intValue(PSTObject object, int propertyId) {
        try {
            Object item = item(object, propertyId);
            if (item == null) {
                return Optional.empty();
            }
            Optional<byte[]> data = bytesFromItemData(item);
            if (data.isEmpty() || data.get().length < 4) {
                return Optional.empty();
            }
            byte[] bytes = data.get();
            int value = (bytes[0] & 0xff)
                    | ((bytes[1] & 0xff) << 8)
                    | ((bytes[2] & 0xff) << 16)
                    | ((bytes[3] & 0xff) << 24);
            return Optional.of(value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public Optional<Integer> valueType(PSTObject object, int propertyId) {
        try {
            Object item = item(object, propertyId);
            if (item == null) {
                return Optional.empty();
            }
            return Optional.of(itemValueTypeField.getInt(item));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private Object item(PSTObject object, int propertyId) throws IllegalAccessException {
        Object rawItems = itemsField.get(object);
        if (!(rawItems instanceof Map<?, ?> items)) {
            return null;
        }
        return items.get(propertyId);
    }

    private Object descriptorItem(PSTObject object, int reference) throws IllegalAccessException {
        Object rawDescriptors = localDescriptorItemsField.get(object);
        if (!(rawDescriptors instanceof Map<?, ?> descriptors)) {
            return null;
        }
        return descriptors.get(reference);
    }

    private Optional<byte[]> bytesFromItemData(Object item) throws IllegalAccessException {
        Object data = itemDataField.get(item);
        if (data instanceof byte[] bytes && bytes.length > 0) {
            return Optional.of(bytes);
        }
        return Optional.empty();
    }

    private Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
