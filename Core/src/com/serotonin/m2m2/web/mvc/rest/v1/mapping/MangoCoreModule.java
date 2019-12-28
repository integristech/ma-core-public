/**
 * Copyright (C) 2014 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.web.mvc.rest.v1.mapping;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.infiniteautomation.mango.io.serial.virtual.VirtualSerialPortConfig;
import com.serotonin.m2m2.web.mvc.rest.v1.model.SuperclassModel;
import com.serotonin.m2m2.web.mvc.rest.v1.model.eventType.EventTypeModel;
import com.serotonin.m2m2.web.mvc.rest.v1.model.publisher.AbstractPublishedPointModel;
import com.serotonin.m2m2.web.mvc.rest.v1.model.publisher.AbstractPublisherModel;

/**
 * Module to extend Jackson JSON rendering
 * 
 * @author Terry Packer
 * 
 */
public class MangoCoreModule extends SimpleModule {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public MangoCoreModule() {
		super("MangoCore", new Version(0, 0, 1, "SNAPSHOT", "com.infiniteautomation",
				"mango"));
	}
	
	@Override
	public void setupModule(SetupContext context) {

		this.addDeserializer(SuperclassModel.class, new SuperclassModelDeserializer());
		this.addDeserializer(AbstractPublisherModel.class, new PublisherModelDeserializer());
		this.addDeserializer(AbstractPublishedPointModel.class, new PublishedPointModelDeserializer());
		this.addDeserializer(EventTypeModel.class, new EventTypeModelDeserializer());
		this.addDeserializer(VirtualSerialPortConfig.class, new VirtualSerialPortConfigDeserializer());
		super.setupModule(context);
	}
}
