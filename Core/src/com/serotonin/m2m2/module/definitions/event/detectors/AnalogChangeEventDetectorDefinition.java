/**
 * Copyright (C) 2016 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.module.definitions.event.detectors;

import com.serotonin.m2m2.db.dao.DataPointDao;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.event.detector.AnalogChangeDetectorVO;

/**
 * @author Terry Packer
 *
 */
public class AnalogChangeEventDetectorDefinition extends PointEventDetectorDefinition<AnalogChangeDetectorVO>{

	public static final String TYPE_NAME = "ANALOG_CHANGE";
		
	@Override
	public String getEventDetectorTypeName() {
		return TYPE_NAME;
	}

	@Override
	public String getDescriptionKey() {
		return "pointEdit.detectors.analogChange";
	}

	@Override
	protected AnalogChangeDetectorVO createEventDetectorVO(DataPointVO dp) {
		return new AnalogChangeDetectorVO(dp);
	}

	@Override
	protected AnalogChangeDetectorVO createEventDetectorVO(int sourceId) {
        return new AnalogChangeDetectorVO(DataPointDao.getInstance().get(sourceId, true));
	}

}
