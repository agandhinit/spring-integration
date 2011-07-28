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

package org.springframework.integration.mongodb.store;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoDbFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.integration.Message;
import org.springframework.integration.MessageHeaders;
import org.springframework.integration.message.GenericMessage;
import org.springframework.integration.store.MessageStore;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import com.mongodb.DBObject;
import com.mongodb.Mongo;

/**
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 */
public class MongoMessageStore implements MessageStore, BeanClassLoaderAware {

	private final static String DEFAULT_COLLECTION_NAME = "messages";

	private final MongoTemplate template;
	
	private volatile String collectionName = DEFAULT_COLLECTION_NAME;

	//private final MongoConverter mongoConverter = new MessageReadingMongoConverter();
	private final MongoConverter mongoConverter = null;

	private volatile ClassLoader classLoader = ClassUtils.getDefaultClassLoader();


	public MongoMessageStore(Mongo mongo, String databaseName) {
		Assert.notNull(mongo, "mongo must not be null");
		Assert.hasText(databaseName, "databaseName must not be empty");
		MongoDbFactory mongoFactory = new SimpleMongoDbFactory(mongo, databaseName);
		MessageReadingMongoConverter converter = new MessageReadingMongoConverter(mongoFactory, new MongoMappingContext());
		this.template = new MongoTemplate(mongoFactory, converter);
//		this.template.setDefaultCollectionName(DEFAULT_COLLECTION_NAME);
//		this.template.createCollection(DEFAULT_COLLECTION_NAME);
	}


	public void setCollectionName(String collectionName) {
		Assert.hasText(collectionName, "collectionName must not be empty");
		this.collectionName = collectionName;
	}

//	public void setUsername(String username) {
//		template.setUsername(username);
//	}
//
//	public void setPassword(String password) {
//		template.setPassword(password);
//	}

	public void setBeanClassLoader(ClassLoader classLoader) {
		Assert.notNull(classLoader, "classLoader must not be null");
		this.classLoader = classLoader;
	}

	public <T> Message<T> addMessage(Message<T> message) {
		this.template.insert(message, collectionName);
		return message;
	}

	public Message<?> getMessage(UUID id) {
		return this.template.findOne(this.idQuery(id), Message.class, this.collectionName);
	}

	public int getMessageCount() {
		// TODO: long to int
		return new Long(this.template.getCollection(DEFAULT_COLLECTION_NAME).getCount()).intValue();
	}

	public Message<?> removeMessage(UUID id) {
		return this.template.findAndRemove(idQuery(id), Message.class, this.collectionName);
	}

	private Query idQuery(UUID id) {
		return new Query(where("_id").is(id.toString()));
	}


	private class MessageReadingMongoConverter extends MappingMongoConverter {

		public MessageReadingMongoConverter(
				MongoDbFactory mongoDbFactory,
				MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext) {
			super(mongoDbFactory, mappingContext);
			// TODO Auto-generated constructor stub
		}


		@Override
		public void afterPropertiesSet() {
			super.afterPropertiesSet();
			Set<Converter<?, ?>> customConverters = new HashSet<Converter<?,?>>();
			customConverters.add(new UuidToStringConverter());
			customConverters.add(new StringToUuidConverter());
			//this.setCustomConverters(customConverters);
		}

		
		@Override
		public void write(Object source, DBObject target) {
			if (source instanceof Message) {
				String payloadType = ((Message<?>) source).getPayload().getClass().getName();
				//target.put("_payloadType", payloadType);
				target.put("_id", ((Message<?>) source).getHeaders().getId().toString());
				//target.put("_id", ((Message<?>) source).getHeaders().getId().toString());
			}
			
//			// TODO: fix this (the base class should handle it via converters?)
//			if (source instanceof UUID) {
//				System.out.println("Converting to UUID");
//				target.put("uuid", ((UUID)source).toString());
//			}
			
			super.write(source, target);
		}


		@Override
		@SuppressWarnings({"unchecked", "rawtypes"})
		public <S> S read(Class<S> clazz, DBObject source) {
			if (!Message.class.equals(clazz)) {
				return super.read(clazz, source);
			}
			Map<String, Object> headers = (Map<String, Object>) source.get("headers");
			Object payload = source.get("payload");
			Object payloadType = source.get("_payloadType");
			if (payloadType != null && payload instanceof DBObject) {
				try {
					Class<?> payloadClass = ClassUtils.forName(payloadType.toString(), classLoader);
					payload = this.read(payloadClass, (DBObject) payload);
				}
				catch (Exception e) {
					throw new IllegalStateException("failed to load class: " + payloadType, e);
				}
			}
			GenericMessage message = new GenericMessage(payload, headers);
			Map innerMap = (Map) new DirectFieldAccessor(message.getHeaders()).getPropertyValue("headers");
			// TODO: unpick this mess
			innerMap.put(MessageHeaders.ID, UUID.fromString(source.get("_id").toString()));
			innerMap.put(MessageHeaders.TIMESTAMP, headers.get(MessageHeaders.TIMESTAMP));
			return (S) message;
		}
	}


	private static class UuidToStringConverter implements Converter<UUID, String> {
		public String convert(UUID source) {
			return source.toString();
		}
	}


	private static class StringToUuidConverter implements Converter<String, UUID> {
		public UUID convert(String source) {
			return UUID.fromString(source);
		}
	}

}
