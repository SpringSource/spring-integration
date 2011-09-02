/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.integration.transformer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Will transform an object graph into a Map.
 * It supports conventional Map (map of maps) where complex attributes are represents as Map values as well as flat Map where keys are valid SpEL expressions documenting the path to the value.
 * It supports Collections, Maps and Arrays which means that for flat maps it will flatten Object's attributes as such:<br>
 * 
 * private Map<String, Map<String, Object>> testMapInMapData;<br>
 * private List<String> departments;<br>
 * private String[] akaNames;<br>
 * 
 * The resulting Map structure will look similar to this:<br>
 * 
 * person.address.mapWithListData['mapWithListTestData'][1]=blah<br>
 * departments[0]=HR<br>
 * person.lname=Case
 * 
 * By default the it will transforme to a flat Map. If you need to transform to a Map of Maps set 'shouldFlattenKeys'
 * attribute to 'false' via {@link ObjectToMapTransformer#setShouldFlattenKeys(boolean)} method.
 * 
 * @author Oleg Zhurakousky
 * @since 2.0
 */
public class ObjectToMapTransformer extends AbstractPayloadTransformer<Object, Map<?,?>> {
	
	private volatile boolean shouldFlattenKeys = true;
	
	public void setShouldFlattenKeys(boolean shouldFlattenKeys) {
		this.shouldFlattenKeys = shouldFlattenKeys;
	}
	
	@SuppressWarnings("unchecked")
	protected Map<String, Object> transformPayload(Object payload) throws Exception {

		ObjectMapper mapper = new ObjectMapper();
		Map<String,Object> result =
		        new ObjectMapper().readValue(mapper.writeValueAsString(payload), Map.class);
		
		if (shouldFlattenKeys){
			result = this.flatenMap(result);
		}
		
		return result;
	}
	
	private Map<String, Object> flatenMap(Map<String,Object> result){
		Map<String,Object> resultMap = new HashMap<String, Object>();
		this.doFlatten("", result, resultMap);
		return resultMap;
	}
	
	private void doFlatten(String propertyPrefix, Map<String,Object> inputMap, Map<String,Object> resultMap){
		if (StringUtils.hasText(propertyPrefix)){
			propertyPrefix = propertyPrefix + ".";
		}
		for (String key : inputMap.keySet()) {
			Object value = inputMap.get(key);
			this.doProcessElement(propertyPrefix + key, value, resultMap);
		}
	}
	
	private void doProcessCollection(String propertyPrefix,  Collection<?> list, Map<String, Object> resultMap) {
		int counter = 0;
		for (Object element : list) {
			this.doProcessElement(propertyPrefix + "[" + counter + "]", element, resultMap);
			counter ++;
		}
	}
	
	@SuppressWarnings("unchecked")
	private void doProcessElement(String propertyPrefix, Object element, Map<String, Object> resultMap){
		if (element instanceof Map){
			this.doFlatten(propertyPrefix, (Map<String, Object>) element, resultMap);
		}
		else if (element instanceof Collection){
			this.doProcessCollection(propertyPrefix, (Collection<?>) element, resultMap);
		} 
		else if (element != null && element.getClass().isArray()){
			Collection<?> collection =  CollectionUtils.arrayToList(element); 
			this.doProcessCollection(propertyPrefix, collection, resultMap);
		}
		else {
			resultMap.put(propertyPrefix, element);
		}
	}
}
