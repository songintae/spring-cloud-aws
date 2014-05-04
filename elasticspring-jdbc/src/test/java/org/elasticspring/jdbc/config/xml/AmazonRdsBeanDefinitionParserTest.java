/*
 * Copyright 2013 the original author or authors.
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

package org.elasticspring.jdbc.config.xml;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.GetUserResult;
import com.amazonaws.services.identitymanagement.model.User;
import com.amazonaws.services.rds.AmazonRDS;
import com.amazonaws.services.rds.AmazonRDSClient;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DescribeDBInstancesRequest;
import com.amazonaws.services.rds.model.DescribeDBInstancesResult;
import com.amazonaws.services.rds.model.Endpoint;
import com.amazonaws.services.rds.model.ListTagsForResourceRequest;
import com.amazonaws.services.rds.model.ListTagsForResourceResult;
import com.amazonaws.services.rds.model.Tag;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.elasticspring.config.AmazonWebserviceClientConfigurationUtils;
import org.elasticspring.context.credentials.CredentialsProviderFactoryBean;
import org.elasticspring.jdbc.datasource.DynamicDataSource;
import org.elasticspring.jdbc.rds.AmazonRdsReadReplicaAwareDataSourceFactoryBean;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.parsing.BeanDefinitionParsingException;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.util.Date;
import java.util.Map;

/**
 * Tests for the {@link org.elasticspring.jdbc.config.xml.AmazonRdsBeanDefinitionParser} bean definition parser
 *
 * @author Agim Emruli
 * @since 1.0
 */
public class AmazonRdsBeanDefinitionParserTest {

	@Rule
	public final ExpectedException expectedException = ExpectedException.none();

	@Test
	public void parseInternal_minimalConfiguration_createsBeanDefinitionWithoutReadReplicas() throws Exception {

		//Arrange
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

		BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(Mockito.class);
		beanDefinitionBuilder.setFactoryMethod("mock");
		beanDefinitionBuilder.addConstructorArgValue(AmazonRDS.class);
		beanFactory.registerBeanDefinition(AmazonWebserviceClientConfigurationUtils.getBeanName(AmazonRDSClient.class.getName()), beanDefinitionBuilder.getBeanDefinition());

		XmlBeanDefinitionReader xmlBeanDefinitionReader = new XmlBeanDefinitionReader(beanFactory);
		xmlBeanDefinitionReader.loadBeanDefinitions(new ClassPathResource(getClass().getSimpleName() + "-minimal.xml", getClass()));

		AmazonRDS client = beanFactory.getBean(AmazonWebserviceClientConfigurationUtils.getBeanName(AmazonRDSClient.class.getName()), AmazonRDS.class);

		Mockito.when(client.describeDBInstances(new DescribeDBInstancesRequest().withDBInstanceIdentifier("test"))).thenReturn(
				new DescribeDBInstancesResult().
						withDBInstances(new DBInstance().
										withDBInstanceStatus("available").
										withDBName("test").
										withDBInstanceIdentifier("test").
										withEngine("mysql").
										withMasterUsername("admin").
										withEndpoint(new Endpoint().
														withAddress("localhost").
														withPort(3306)
										).withReadReplicaDBInstanceIdentifiers("read1")
						)
		);

		//Act
		DataSource dataSource = beanFactory.getBean(DataSource.class);

		//Assert
		Assert.assertTrue(dataSource instanceof DynamicDataSource);
	}

	@Test
	public void parseInternal_readReplicaSupportEnabled_configuresReadReplicaEnabledFactoryBean() throws Exception {

		//Arrange
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

		BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(Mockito.class);
		beanDefinitionBuilder.setFactoryMethod("mock");
		beanDefinitionBuilder.addConstructorArgValue(AmazonRDS.class);
		beanFactory.registerBeanDefinition(AmazonWebserviceClientConfigurationUtils.getBeanName(AmazonRDSClient.class.getName()), beanDefinitionBuilder.getBeanDefinition());

		XmlBeanDefinitionReader xmlBeanDefinitionReader = new XmlBeanDefinitionReader(beanFactory);
		xmlBeanDefinitionReader.loadBeanDefinitions(new ClassPathResource(getClass().getSimpleName() + "-readReplicaEnabled.xml", getClass()));

		//Act
		BeanDefinition beanDefinition = beanFactory.getBeanDefinition("dataSource");

		//Assert
		Assert.assertEquals(AmazonRdsReadReplicaAwareDataSourceFactoryBean.class.getName(), beanDefinition.getBeanClassName());
	}

	@Test
	public void parseInternal_noCredentialsDefined_reportsError() throws Exception {
		this.expectedException.expect(BeanCreationException.class);
		this.expectedException.expectMessage(CredentialsProviderFactoryBean.CREDENTIALS_PROVIDER_BEAN_NAME);

		//noinspection ResultOfObjectAllocationIgnored
		new ClassPathXmlApplicationContext(getClass().getSimpleName() + "-noCredentials.xml", getClass());
	}

	@Test
	public void parseInternal_fullConfiguration_createsBeanDefinitionWithoutReadReplicas() throws Exception {

		//Arrange
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

		BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(Mockito.class);
		beanDefinitionBuilder.setFactoryMethod("mock");
		beanDefinitionBuilder.addConstructorArgValue(AmazonRDS.class);
		beanFactory.registerBeanDefinition(AmazonWebserviceClientConfigurationUtils.getBeanName(AmazonRDSClient.class.getName()), beanDefinitionBuilder.getBeanDefinition());

		XmlBeanDefinitionReader xmlBeanDefinitionReader = new XmlBeanDefinitionReader(beanFactory);
		xmlBeanDefinitionReader.loadBeanDefinitions(new ClassPathResource(getClass().getSimpleName() + "-fullConfiguration.xml", getClass()));

		AmazonRDS client = beanFactory.getBean(AmazonWebserviceClientConfigurationUtils.getBeanName(AmazonRDSClient.class.getName()), AmazonRDS.class);

		Mockito.when(client.describeDBInstances(new DescribeDBInstancesRequest().withDBInstanceIdentifier("test"))).thenReturn(
				new DescribeDBInstancesResult().
						withDBInstances(new DBInstance().
										withDBInstanceStatus("available").
										withDBName("test").
										withDBInstanceIdentifier("test").
										withEngine("mysql").
										withEndpoint(new Endpoint().
														withAddress("localhost").
														withPort(3306)
										)
						)
		);

		BeanDefinition definition = beanFactory.getBeanDefinition("dataSource");
		Assert.assertEquals("test", definition.getConstructorArgumentValues().getArgumentValue(1, String.class).getValue());
		Assert.assertEquals("password", definition.getConstructorArgumentValues().getArgumentValue(2, String.class).getValue());
		Assert.assertEquals("myUser", definition.getPropertyValues().getPropertyValue("username").getValue());

		DataSource dataSource = beanFactory.getBean(DataSource.class);

		//Assert
		Assert.assertNotNull(dataSource);
		Assert.assertTrue(dataSource instanceof DynamicDataSource);
	}

	@Test
	public void parseInternal_dataSourceWithConfiguredPoolAttributes_poolAttributesConfigured() throws Exception {

		//Arrange
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

		XmlBeanDefinitionReader xmlBeanDefinitionReader = new XmlBeanDefinitionReader(beanFactory);
		xmlBeanDefinitionReader.loadBeanDefinitions(new ClassPathResource(getClass().getSimpleName() + "-poolAttributes.xml", getClass()));

		BeanDefinition definition = beanFactory.getBeanDefinition("dataSource");

		//Act
		BeanDefinition dataSourceFactory = (BeanDefinition) definition.getPropertyValues().getPropertyValue("dataSourceFactory").getValue();

		//Assert
		Assert.assertEquals("foo=bar", dataSourceFactory.getPropertyValues().getPropertyValue("connectionProperties").getValue());
		Assert.assertEquals(Boolean.TRUE.toString(), dataSourceFactory.getPropertyValues().getPropertyValue("defaultAutoCommit").getValue());
		Assert.assertEquals("mySchema", dataSourceFactory.getPropertyValues().getPropertyValue("defaultCatalog").getValue());
		Assert.assertEquals(Boolean.TRUE.toString(), dataSourceFactory.getPropertyValues().getPropertyValue("defaultReadOnly").getValue());
		Assert.assertEquals("2", dataSourceFactory.getPropertyValues().getPropertyValue("defaultTransactionIsolation").getValue());
		Assert.assertEquals("10", dataSourceFactory.getPropertyValues().getPropertyValue("initialSize").getValue());
		Assert.assertEquals("SET CURRENT SCHEMA", dataSourceFactory.getPropertyValues().getPropertyValue("initSQL").getValue());
		Assert.assertEquals(Boolean.TRUE.toString(), dataSourceFactory.getPropertyValues().getPropertyValue("logAbandoned").getValue());
		Assert.assertEquals("10", dataSourceFactory.getPropertyValues().getPropertyValue("maxActive").getValue());
		Assert.assertEquals("5", dataSourceFactory.getPropertyValues().getPropertyValue("maxIdle").getValue());
		Assert.assertEquals("10000", dataSourceFactory.getPropertyValues().getPropertyValue("maxWait").getValue());
		Assert.assertEquals("60000", dataSourceFactory.getPropertyValues().getPropertyValue("minEvictableIdleTimeMillis").getValue());
		Assert.assertEquals("20", dataSourceFactory.getPropertyValues().getPropertyValue("minIdle").getValue());
		Assert.assertEquals("61", dataSourceFactory.getPropertyValues().getPropertyValue("removeAbandonedTimeout").getValue());
		Assert.assertEquals(Boolean.TRUE.toString(), dataSourceFactory.getPropertyValues().getPropertyValue("testOnBorrow").getValue());
		Assert.assertEquals(Boolean.TRUE.toString(), dataSourceFactory.getPropertyValues().getPropertyValue("testOnReturn").getValue());
		Assert.assertEquals(Boolean.TRUE.toString(), dataSourceFactory.getPropertyValues().getPropertyValue("testWhileIdle").getValue());
		Assert.assertEquals("4000", dataSourceFactory.getPropertyValues().getPropertyValue("timeBetweenEvictionRunsMillis").getValue());
		Assert.assertEquals("SELECT 1", dataSourceFactory.getPropertyValues().getPropertyValue("validationQuery").getValue());
		Assert.assertEquals("myValidator", dataSourceFactory.getPropertyValues().getPropertyValue("validatorClassName").getValue());
	}

	@Test
	//As we provide default in the schema for better code completion we should check if they match to the underlying pool defaults
	public void parseInternal_defaultPoolAttribute_matchesPoolConfiguration() throws Exception {
		//Arrange
		PoolProperties poolProperties = new PoolProperties();

		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

		XmlBeanDefinitionReader xmlBeanDefinitionReader = new XmlBeanDefinitionReader(beanFactory);
		xmlBeanDefinitionReader.loadBeanDefinitions(new ClassPathResource(getClass().getSimpleName() + "-defaultPoolAttributes.xml", getClass()));

		//Act
		BeanDefinition definition = beanFactory.getBeanDefinition("dataSource");
		BeanDefinition dataSourceFactory = (BeanDefinition) definition.getPropertyValues().getPropertyValue("dataSourceFactory").getValue();

		//Assert
		BeanWrapper beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(poolProperties);

		for (PropertyValue propertyValue : dataSourceFactory.getPropertyValues().getPropertyValueList()) {
			Assert.assertEquals(beanWrapper.getPropertyValue(propertyValue.getName()).toString(), propertyValue.getValue());
		}
	}

	@Test
	public void parseInternal_customRegionConfigured_amazonRdsClientWithCustomRegionConfigured() throws Exception {

		//Arrange
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

		XmlBeanDefinitionReader xmlBeanDefinitionReader = new XmlBeanDefinitionReader(beanFactory);
		xmlBeanDefinitionReader.loadBeanDefinitions(new ClassPathResource(getClass().getSimpleName() + "-customRegion.xml", getClass()));

		//Act
		AmazonRDS amazonRDS = beanFactory.getBean(AmazonRDS.class);

		//Assert
		//have to use reflection utils
		Assert.assertEquals("https://rds.eu-west-1.amazonaws.com", ReflectionTestUtils.getField(amazonRDS, "endpoint").toString());
	}

	@Test
	public void parseInternal_customRegionProviderConfigured_amazonRdsClientWithCustomRegionConfiguredThatIsReturnedFromRegionProvider() throws Exception {

		//Arrange
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

		XmlBeanDefinitionReader xmlBeanDefinitionReader = new XmlBeanDefinitionReader(beanFactory);
		xmlBeanDefinitionReader.loadBeanDefinitions(new ClassPathResource(getClass().getSimpleName() + "-customRegionProvider.xml", getClass()));

		//Act
		AmazonRDS amazonRDS = beanFactory.getBean(AmazonRDS.class);

		//Assert
		//have to use reflection utils
		Assert.assertEquals("https://rds.eu-west-1.amazonaws.com", ReflectionTestUtils.getField(amazonRDS, "endpoint").toString());

	}

	@Test
	public void parseInternal_customRegionProviderAndRegionConfigured_reportsError() throws Exception {
		//Arrange
		this.expectedException.expect(BeanDefinitionParsingException.class);
		this.expectedException.expectMessage("not be used together");

		//Act
		//noinspection ResultOfObjectAllocationIgnored
		new ClassPathXmlApplicationContext(getClass().getSimpleName() + "-customRegionProviderAndRegion.xml", getClass());

		//Assert
	}

	@Test
	public void parseInternal_userTagsDefined_createsUserTagBeanDefinition() throws Exception {

		//Arrange
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

		BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(Mockito.class);
		beanDefinitionBuilder.setFactoryMethod("mock");
		beanDefinitionBuilder.addConstructorArgValue(AmazonRDS.class);
		beanFactory.registerBeanDefinition(AmazonWebserviceClientConfigurationUtils.getBeanName(AmazonRDSClient.class.getName()), beanDefinitionBuilder.getBeanDefinition());

		BeanDefinitionBuilder identityBuilder = BeanDefinitionBuilder.rootBeanDefinition(Mockito.class);
		identityBuilder.setFactoryMethod("mock");
		identityBuilder.addConstructorArgValue(AmazonIdentityManagement.class);
		beanFactory.registerBeanDefinition(AmazonWebserviceClientConfigurationUtils.getBeanName(AmazonIdentityManagement.class.getName()), identityBuilder.getBeanDefinition());

		XmlBeanDefinitionReader xmlBeanDefinitionReader = new XmlBeanDefinitionReader(beanFactory);
		xmlBeanDefinitionReader.loadBeanDefinitions(new ClassPathResource(getClass().getSimpleName() + "-userTags.xml", getClass()));

		AmazonRDS client = beanFactory.getBean(AmazonWebserviceClientConfigurationUtils.getBeanName(AmazonRDSClient.class.getName()), AmazonRDS.class);
		AmazonIdentityManagement amazonIdentityManagement = beanFactory.getBean(AmazonWebserviceClientConfigurationUtils.getBeanName(AmazonIdentityManagement.class.getName()), AmazonIdentityManagement.class);

		Mockito.when(amazonIdentityManagement.getUser()).thenReturn(new GetUserResult().withUser(new User("/", "aemruli", "123456789012", "arn:aws:iam::1234567890:user/aemruli", new Date())));
		Mockito.when(client.listTagsForResource(new ListTagsForResourceRequest().withResourceName("arn:aws:rds:us-west-2:1234567890:db:test"))).thenReturn(new ListTagsForResourceResult().withTagList(
				new Tag().withKey("key1").withValue("value2")
		));

		//Act
		Map<?,?> dsTags = beanFactory.getBean("dsTags", Map.class);

		//Assert
		Assert.assertEquals("value2", dsTags.get("key1"));
	}
}