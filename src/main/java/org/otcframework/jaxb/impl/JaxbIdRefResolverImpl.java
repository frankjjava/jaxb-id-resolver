/**
* Copyright (c) otcframework.org
*
* @author  Franklin J Abel (frank.a.otc@gmail.com)
* @version 1.0
* @since   2022-08-31 
*
* This file is part of the OTC framework's JAXB ID Resolver project.
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*/
package org.otcframework.jaxb.impl;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.xml.bind.annotation.XmlElementRefs;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.lang3.ClassUtils;
import org.otcframework.jaxb.CompiledStatus;
import org.otcframework.jaxb.util.PackagesFilterUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public  class JaxbIdRefResolverImpl<T> extends AbstractJaxbIdRefResolver<T> {

	private static final Logger LOGGER = LoggerFactory.getLogger(JaxbIdRefResolverImpl.class);

	protected Boolean unsetTimeZoneInXmlGregorianCalandar;
	protected Boolean fixXmlIdRefFields;

	private Set<Class<?>> compiledClasses;
	private static Map<Class<?>, Set<Field>> xmlIdFieldsCache;
	private static Map<Class<?>, Set<Field>> xmlIdRefFieldsCache;
	private Map<Class<?>, Set<Field>> xmlGregorianCalendarFieldsCache;
	private List<String> compileClasses;
			
	public List<String> getCompileClasses() {
		return compileClasses;
	}

	public void setCompileClasses(List<String> compileClasses) {
		this.compileClasses = compileClasses;
	}

	@PostConstruct
	public void initialize() {
		for (String clsName : compileClasses) {
			Class<?> cls;
			try {
				cls = ClassUtils.getClass(clsName);
				compile(cls);
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		LOGGER.info("Completed compilations.");
		return;
	}

	@Override
	public T resolve(T parentObject) {
		if (parentObject == null) {
			return null;
		}
		try {
			if (!fixXmlIdRefFields && !unsetTimeZoneInXmlGregorianCalandar) {
				LOGGER.warn("Both 'fixXmlIdRefFields' and 'xmlgregoriancalendar.unsetTimeZone' are turned-off or not set.");
				return parentObject;
			}
			compile(parentObject);
			if (fixXmlIdRefFields) {
				Map<String, Object> xmlIdObjects = fetchXmlIdValues(parentObject, parentObject.getClass(), null);
				if (xmlIdObjects != null) {
					fixXmlIdRefValues(parentObject, parentObject.getClass(), xmlIdObjects, null);
				}
			}
			if (unsetTimeZoneInXmlGregorianCalandar) {
				fixXmlGregorianCalendarValues(parentObject, parentObject.getClass(), null);
			}
		} catch (Exception ex) {
			LOGGER.error("", ex);
		}
		return parentObject;
	}
	
	@Override
	public T prefixIds(T parentObject, String thirdpartyId) {
		if (parentObject == null) {
			return null;
		}
		compile(parentObject);
		thirdpartyId = thirdpartyId.concat("-");
		fetchFieldValueAndPrefix(parentObject, thirdpartyId, true);
		return parentObject;
	}
	
	@Override
	public T stripPrefixInIds(T parentObject, String thirdpartyId) {
		if (parentObject == null) {
			return null;
		}
		compile(parentObject);
		thirdpartyId = thirdpartyId.concat("-");
		fetchFieldValueAndPrefix(parentObject, thirdpartyId, false);
		return parentObject;
	}
	
	@Override
	public void fixXmlGregorianCalendarValues(T parentObject) {
		compile(parentObject);
		fixXmlGregorianCalendarValues(parentObject, parentObject.getClass(), null);
		return;
	}
	
	private void fetchFieldValueAndPrefix(Object parentObject, String thirdpartyId, boolean isPrefix) {
		Class<?> keyClass = parentObject.getClass();
		while (keyClass != null) {
			Set<Field> fields = xmlIdFieldsCache.get(keyClass);
			if (fields != null) {
				for (Field field : fields) {
					Object fldValue = readFieldValue(parentObject, field);
					if (fldValue == null) {
						continue;
					}
					if (fldValue instanceof String) {
						if (isPrefix) {
							fldValue = thirdpartyId.concat((String)fldValue);
						} else {
							fldValue = ((String) fldValue).replace(thirdpartyId, "");
						}
						try {
							boolean isAccessible = field.isAccessible();
							field.setAccessible(true);
							field.set(parentObject, fldValue);
							field.setAccessible(isAccessible);
						} catch (IllegalArgumentException | IllegalAccessException e) {
							LOGGER.warn(e.getMessage());
						}
						continue;
					}
					Class<?> valueType = fldValue.getClass();
					if (valueType.isArray()) {
						Object[] objects = (Object[]) fldValue;
						if (objects.length == 0) {
							continue;
						}
						for (Object member : objects) {
							Class<?> memberType = member.getClass();
							if (PackagesFilterUtil.isFilteredPackage(memberType)) {
								fetchFieldValueAndPrefix(member, thirdpartyId, isPrefix);
							}
						}
						continue;
					} else if (List.class.isAssignableFrom(valueType)) {
						List<Object> objects = (List) fldValue;
						if (objects.isEmpty()) {
							continue;
						}
						for (Object member : objects) {
							Class<?> memberType = member.getClass();
							if (PackagesFilterUtil.isFilteredPackage(memberType)) {
								fetchFieldValueAndPrefix(member, thirdpartyId, isPrefix);
							}
						}
						continue;
					} else if (PackagesFilterUtil.isFilteredPackage(valueType)) {
						fetchFieldValueAndPrefix(fldValue, thirdpartyId, isPrefix);
						continue;
					}
					if (field.getAnnotation(XmlID.class) == null) {
						continue;
					}
				}
			}
			keyClass = keyClass.getSuperclass();
			if (!PackagesFilterUtil.isFilteredPackage(keyClass)) {
				keyClass = null;
			}
		}
		return;
	}
	
	private void compile(T parentObject) {
		if (compiledClasses == null || !compiledClasses.contains(parentObject.getClass())) {
			compile(parentObject.getClass());
		}
		return;
	}
	
	@Override
	public CompiledStatus compile(Class<?> parentClass) {
		CompiledStatus compiledStatus = null;
		Class<?> keyClass = parentClass;
		String parentClsName = parentClass.getName();
		while (keyClass != null) {	
			Field[] fields = keyClass.getDeclaredFields();
			if (fields != null) {
				for (Field field : fields) {
					Class<?> fieldType = field.getType();
					if (fieldType.isEnum()) {
						continue;
					}
					// newly added code to handle classes with self-referencing fields
					if (shouldRegister(field)) {
						compiledStatus = updateRegistry(compiledStatus, keyClass, field);
						continue;
					}
					// END
					Class<?> componentType = null;
					if (fieldType.isArray()) {
						componentType = fieldType.getComponentType();
					} else if (List.class.isAssignableFrom(fieldType)) {
						Type type = field.getGenericType();
						ParameterizedType parameterizedType = (ParameterizedType) type;
						type = parameterizedType.getActualTypeArguments()[0];
						if (type instanceof Class) { 
							componentType = (Class<?>) type;
						}
					} else {
						componentType = fieldType;
					}
					if (componentType != null && PackagesFilterUtil.isFilteredPackage(componentType)) {
						CompiledStatus childrenCompiledStatus = null;
						// newly added code to handle classes with self-referencing fields
						if (parentClsName.equalsIgnoreCase(componentType.getName())) {
							if (shouldRegister(field)) {
								childrenCompiledStatus = updateRegistry(compiledStatus, keyClass, field);
							}
						} else {
						// END
							childrenCompiledStatus = compile(componentType);
						}
						if (childrenCompiledStatus != null) {
							updateCache(childrenCompiledStatus, keyClass, field);
							compiledStatus = mergeCompiledStatus(compiledStatus, childrenCompiledStatus);
						}
					}
				}
			}
			keyClass = keyClass.getSuperclass();
			if (!PackagesFilterUtil.isFilteredPackage(keyClass)) {
				keyClass = null;
			}
		}
		if (compiledClasses == null) {
			compiledClasses = new HashSet<>();
		}
		compiledClasses.add(parentClass);
		return compiledStatus;
	}
	
	private boolean shouldRegister(Field field) {
		Class<?> fieldType = field.getType();
		if (unsetTimeZoneInXmlGregorianCalandar && fieldType.equals(XMLGregorianCalendar.class)) {
			return true;
		}		
		if (field.getAnnotation(XmlIDREF.class) != null || field.getAnnotation(XmlElementRefs.class) != null) {
			return true;
		}
		if (field.getAnnotation(XmlID.class) != null) {
			return true;
		}
		return false;
	}
	
	private CompiledStatus updateRegistry(CompiledStatus compiledStatus, Class<?> keyClass, Field field) {
		Class<?> fieldType = field.getType();
		if (unsetTimeZoneInXmlGregorianCalandar && fieldType.equals(XMLGregorianCalendar.class)) {
			compiledStatus = addToXmlGregorianCalendarCache(keyClass, field, compiledStatus);
			return compiledStatus;
		}
		if (field.getAnnotation(XmlIDREF.class) != null || field.getAnnotation(XmlElementRefs.class) != null) {
			compiledStatus = addToXmlIdRefCache(keyClass, field, compiledStatus);
			return compiledStatus;
		}
		if (field.getAnnotation(XmlID.class) != null) {
			compiledStatus = addToXmlIdCache(keyClass, field, compiledStatus);
			return compiledStatus;
		}
		return compiledStatus;
	}
	
	private Map<String, Object> fetchXmlIdValues(Object parentObject, Class<?> parentClass, Map<String, Object> xmlIdObjects) {
		Class<?> keyClass = parentClass;
		while (keyClass != null) {
			Set<Field> fields = xmlIdFieldsCache.get(keyClass);
			if (fields != null) {
				for (Field field : fields) {
					Object fldValue = readFieldValue(parentObject, field);
					if (fldValue == null) {
						continue;
					}
					Class<?> valueType = fldValue.getClass();
					if (valueType.isArray()) {
						Object[] objects = (Object[]) fldValue;
						if (objects.length == 0) {
							continue;
						}
						for (Object member : objects) {
							Class<?> memberType = member.getClass();
							if (PackagesFilterUtil.isFilteredPackage(memberType)) {
								xmlIdObjects = fetchXmlIdValues(member, memberType, xmlIdObjects);
							}
						}
						continue;
					} else if (List.class.isAssignableFrom(valueType)) {
						List<Object> objects = (List) fldValue;
						if (objects.isEmpty()) {
							continue;
						}
						for (Object member : objects) {
							Class<?> memberType = member.getClass();
							if (PackagesFilterUtil.isFilteredPackage(memberType)) {
								xmlIdObjects = fetchXmlIdValues(member, memberType, xmlIdObjects);
							}
						}
						continue;
					} else if (PackagesFilterUtil.isFilteredPackage(valueType)) {
						xmlIdObjects = fetchXmlIdValues(fldValue, valueType, xmlIdObjects);
						continue;
					}
					if (field.getAnnotation(XmlID.class) == null) {
						continue;
					}
					if (xmlIdObjects == null) {
						xmlIdObjects = new HashMap<>();
					}
					if (fldValue instanceof String) {
						xmlIdObjects.put((String) fldValue, parentObject);
					}
				}
			}
			keyClass = keyClass.getSuperclass();
			if (!PackagesFilterUtil.isFilteredPackage(keyClass)) {
				keyClass = null;
			}
		}
		return xmlIdObjects;
	}

	private Map<Object, Object> fixXmlIdRefValues(Object parentObject, Class<?> parentClass, Map<String, Object> xmlIdObjects, Map<Object, Object> xmlIdRefObjects) {
		Class<?> keyClass = parentClass;
		while (keyClass != null) {
			Set<Field> fields = xmlIdRefFieldsCache.get(keyClass);
			if (fields != null) {
				for (Field field : fields) {
					Object fldValue = readFieldValue(parentObject, field);
					if (fldValue == null) {
						continue;
					}
					Class<?> valueType = fldValue.getClass();
					if (field.getAnnotation(XmlIDREF.class) != null || field.getAnnotation(XmlElementRefs.class) != null) {
						xmlIdRefObjects = createIdRef(parentObject, field, fldValue, xmlIdObjects, xmlIdRefObjects);
					} else if (valueType.isArray()) {
						Object[] objects = (Object[]) fldValue;
						if (objects.length == 0) {
							continue;
						}
						for (Object member : objects) {
							Class<?> memberType = member.getClass();
							if (PackagesFilterUtil.isFilteredPackage(memberType)) {
								xmlIdRefObjects = fixXmlIdRefValues(member, memberType, xmlIdObjects, xmlIdRefObjects);
							}
						}
					} else if (fldValue instanceof List) {
						for (Object member : (List) fldValue) {
							Class<?> memberType = member.getClass();
							if (PackagesFilterUtil.isFilteredPackage(memberType)) {
								xmlIdRefObjects = fixXmlIdRefValues(member, memberType, xmlIdObjects, xmlIdRefObjects);
							}
						}
					} else if (PackagesFilterUtil.isFilteredPackage(valueType)) {
						xmlIdRefObjects = fixXmlIdRefValues(fldValue, valueType, xmlIdObjects, xmlIdRefObjects);
					}
				}
			}
			keyClass = keyClass.getSuperclass();
			if (!PackagesFilterUtil.isFilteredPackage(keyClass)) {
				keyClass = null;
			}
		}
		return xmlIdRefObjects;
	}
	
	private Map<Field, Object> fixXmlGregorianCalendarValues(Object parentObject, Class<?> parentClass, Map<Field, Object> miscellaneousFixes) {
		Class<?> keyClass = parentClass;
		while (keyClass != null) {
			Set<Field> fields = xmlGregorianCalendarFieldsCache.get(keyClass);
			if (fields != null) {
				for (Field field : fields) {
					Object fldValue = readFieldValue(parentObject, field);
					if (fldValue == null) {
						continue;
					}
					Class<?> valueType = fldValue.getClass();
					if (fldValue instanceof XMLGregorianCalendar) {
						miscellaneousFixes = unsetTimeZoneInXmlGregorianCalendar(field, (XMLGregorianCalendar) fldValue, miscellaneousFixes);
					} else if (valueType.isArray()) {
						Object[] objects = (Object[]) fldValue;
						if (objects.length == 0) {
							continue;
						}
						for (Object member : objects) {
							Class<?> memberType = member.getClass();
							if (PackagesFilterUtil.isFilteredPackage(memberType)) {
								miscellaneousFixes = fixXmlGregorianCalendarValues(member, memberType, miscellaneousFixes);
							}
						}
					} else if (fldValue instanceof List) {
						for (Object member : (List) fldValue) {
							Class<?> memberType = member.getClass();
							if (PackagesFilterUtil.isFilteredPackage(memberType)) {
								miscellaneousFixes = fixXmlGregorianCalendarValues(member, memberType, miscellaneousFixes);
							}
						}
					} else if (PackagesFilterUtil.isFilteredPackage(valueType)) {
						miscellaneousFixes = fixXmlGregorianCalendarValues(fldValue, valueType, miscellaneousFixes);
					}
				}
			}
			keyClass = keyClass.getSuperclass();
			if (!PackagesFilterUtil.isFilteredPackage(keyClass)) {
				keyClass = null;
			}
		}
		return miscellaneousFixes;
	}

	private Map<Object, Object> createIdRef(Object parentObject, Field field, Object fldValue, Map<String, Object> xmlIdObjects, Map<Object, Object> xmlIdRefObjects) {
		if (fldValue == null) {
			return xmlIdRefObjects;
		}
		Class<?> valueType = fldValue.getClass();
		if (valueType.isArray()) {
			List<Object> objects = Arrays.asList((Object[]) fldValue);
			xmlIdRefObjects = createIdRef(parentObject, field, objects, valueType, xmlIdObjects, xmlIdRefObjects);
		} else if (fldValue instanceof List) {
			List<Object> objects = (List) fldValue;
			xmlIdRefObjects = createIdRef(parentObject, field, objects, valueType, xmlIdObjects, xmlIdRefObjects);
		} else if (fldValue instanceof String) {
			List<Object> idRefs = createIdRefCollection(xmlIdObjects, (String) fldValue);
			if (idRefs == null) {
				return xmlIdRefObjects;
			}
			Object idRef = idRefs.get(0);
			field.setAccessible(true);
			try {
				field.set(parentObject, idRef);
				if (xmlIdRefObjects == null) {
					xmlIdRefObjects = new HashMap<>();
				}
				xmlIdRefObjects.put(fldValue, idRef);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				LOGGER.warn(e.getMessage());
			}
			field.setAccessible(false);
		}
		return xmlIdRefObjects;
	}
	
	private Map<Object, Object> createIdRef(Object parentObject, Field field, List<Object> objects, Class<?> valueType, Map<String, Object> xmlIdObjects, Map<Object, Object> xmlIdRefObjects) {
		if (objects == null || objects.isEmpty()) {
			return xmlIdRefObjects;
		}
		List<Object> lstObjects = null;
		for (Object member : objects) {
			if (!(member instanceof String)) {
				Class<?> valType = member.getClass();
				boolean isPrimitiveOrWrapped = ClassUtils.isPrimitiveOrWrapper(valType);
				if (!isPrimitiveOrWrapped) {
					fixXmlIdRefValues(member, valType, xmlIdObjects, xmlIdRefObjects);
					continue;
				}
				member = member.toString();
				if (member == null) {
					continue;
				}
			}
			List<Object> idRefs = createIdRefCollection(xmlIdObjects, (String) member);
			if (idRefs != null) {
				if (lstObjects == null) {
					lstObjects = new ArrayList<>();
				}
				lstObjects.addAll(idRefs);
			} else {
				String msg = new StringBuilder("Error! ID field not found for field ").append(field.getDeclaringClass().getName())
						.append(".").append(field.getName()).toString();
				LOGGER.error(msg);
			}
		}
		if (lstObjects != null) {
			field.setAccessible(true);
			try {
				if (valueType.isArray()) {
					field.set(parentObject, lstObjects.toArray());
				} else {
					field.set(parentObject, lstObjects);
				}
			} catch (IllegalArgumentException | IllegalAccessException e) {
				LOGGER.warn(e.getMessage());
			}
			field.setAccessible(false);
		}
		return xmlIdRefObjects;
	}
	
	private List<Object> createIdRefCollection(Map<String, Object> xmlIdObjects, String idRef) {
		if (idRef == null) {
			return null;
		}
		List<Object> lstIdRefs = null;
		String[] refs = idRef.split(" ");
		for (String ref : refs) {
			ref = ref.trim();
			Object idObject = xmlIdObjects.get(ref);
			if (idObject != null) {
				if (lstIdRefs == null) {
					lstIdRefs = new ArrayList<>();
				}
				lstIdRefs.add(idObject);
			}
		}
		return lstIdRefs;
	}
	
	private CompiledStatus updateCache(CompiledStatus compiledStatus, Class<?> parentClass, Field field) {
		if (compiledStatus.isXmlGregorianCalendarFieldExists()) {
			compiledStatus = addToXmlGregorianCalendarCache(parentClass, field, compiledStatus);
		}
		if (compiledStatus.isXmlIdFieldExists()) {
			compiledStatus = addToXmlIdCache(parentClass, field, compiledStatus);
		}
		if (compiledStatus.isXmlIdRefFieldExists()) {
			compiledStatus = addToXmlIdRefCache(parentClass, field, compiledStatus);
		}
		return compiledStatus;
	}

	private CompiledStatus mergeCompiledStatus(CompiledStatus compiledStatus, CompiledStatus newCompiledStatus) {
		if (compiledStatus == null) {
			compiledStatus = newCompiledStatus;
		} else {
			if (newCompiledStatus.isXmlGregorianCalendarFieldExists()) {
				compiledStatus.setXmlGregorianCalendarFieldExists(newCompiledStatus.isXmlGregorianCalendarFieldExists());
			}
			if (newCompiledStatus.isXmlIdFieldExists()) {
				compiledStatus.setXmlIdFieldExists(newCompiledStatus.isXmlIdFieldExists());
			}
			if (newCompiledStatus.isXmlIdRefFieldExists()) {
				compiledStatus.setXmlIdRefFieldExists(newCompiledStatus.isXmlIdRefFieldExists());
			}
		}
		return compiledStatus;
	}
	
	private CompiledStatus addToXmlIdCache(Class<?> parentClass, Field field, CompiledStatus compiledStatus) {
		if (xmlIdFieldsCache == null) {
			xmlIdFieldsCache = new HashMap<>();
		}
		if (xmlIdFieldsCache.get(parentClass) == null) {
			xmlIdFieldsCache.put(parentClass, new HashSet<>());
		}
		xmlIdFieldsCache.get(parentClass).add(field);
		if (compiledStatus == null) {
			compiledStatus = new CompiledStatus();
		}
		compiledStatus.setXmlIdFieldExists(true);
		return compiledStatus;
	}
	
	private CompiledStatus addToXmlIdRefCache(Class<?> parentClass, Field field, CompiledStatus compiledStatus) {
		if (xmlIdRefFieldsCache == null) {
			xmlIdRefFieldsCache = new HashMap<>();
		}
		if (xmlIdRefFieldsCache.get(parentClass) == null) {
			xmlIdRefFieldsCache.put(parentClass, new HashSet<>());
		}
		xmlIdRefFieldsCache.get(parentClass).add(field);
		if (compiledStatus == null) {
			compiledStatus = new CompiledStatus();
		}
		compiledStatus.setXmlIdRefFieldExists(true);
		return compiledStatus;
	}
	
	private CompiledStatus addToXmlGregorianCalendarCache(Class<?> parentClass, Field field, CompiledStatus compiledStatus) {
		if (xmlGregorianCalendarFieldsCache == null) {
			xmlGregorianCalendarFieldsCache = new HashMap<>();
		}
		if (xmlGregorianCalendarFieldsCache.get(parentClass) == null) {
			xmlGregorianCalendarFieldsCache.put(parentClass, new HashSet<>());
		}
		xmlGregorianCalendarFieldsCache.get(parentClass).add(field);
		if (compiledStatus == null) {
			compiledStatus = new CompiledStatus();
		}
		compiledStatus.setXmlGregorianCalendarFieldExists(true);
		return compiledStatus;
	}
	
	private Map<Field, Object> unsetTimeZoneInXmlGregorianCalendar(Field field, XMLGregorianCalendar xmlGregorianCalendar, 
			Map<Field, Object> miscellaneousFixes) {
		xmlGregorianCalendar.setTimezone(DatatypeConstants.FIELD_UNDEFINED);
		if (miscellaneousFixes == null) {
			miscellaneousFixes = new HashMap<>();
		}
		miscellaneousFixes.put(field, xmlGregorianCalendar);
		return miscellaneousFixes;
	}
	
	private Object readFieldValue(Object parentObject, Field field) {
		Object fldValue = null;
		try {
			field.setAccessible(true);
			fldValue = field.get(parentObject);
			field.setAccessible(false);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			LOGGER.warn(e.getMessage());
			throw new RuntimeException(e);
		}
		return fldValue;
	}
	
}
