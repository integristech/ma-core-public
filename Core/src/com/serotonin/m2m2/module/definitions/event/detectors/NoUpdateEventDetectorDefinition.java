/**
 * Copyright (C) 2016 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.module.definitions.event.detectors;

import com.serotonin.m2m2.db.dao.DataPointDao;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.event.detector.NoUpdateDetectorVO;

/**
 * @author Terry Packer
 *
 */
public class NoUpdateEventDetectorDefinition extends PointEventDetectorDefinition<NoUpdateDetectorVO>{

	public static final String TYPE_NAME = "NO_UPDATE";
		
	@Override
	public String getEventDetectorTypeName() {
		return TYPE_NAME;
	}

	@Override
	public String getDescriptionKey() {
		return "pointEdit.detectors.noUpdate";
	}

	@Override
	protected NoUpdateDetectorVO createEventDetectorVO(DataPointVO vo) {
		return new NoUpdateDetectorVO(vo);
	}

	@Override
	protected NoUpdateDetectorVO createEventDetectorVO(int sourceId) {
        return new NoUpdateDetectorVO(DataPointDao.getInstance().get(sourceId, true));
	}

}
