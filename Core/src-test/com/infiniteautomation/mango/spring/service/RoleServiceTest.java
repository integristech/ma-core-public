/**
 * Copyright (C) 2019  Infinite Automation Software. All rights reserved.
 */
package com.infiniteautomation.mango.spring.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.infiniteautomation.mango.util.exception.ValidationException;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.DataTypes;
import com.serotonin.m2m2.db.dao.DataPointDao;
import com.serotonin.m2m2.db.dao.DataSourceDao;
import com.serotonin.m2m2.db.dao.RoleDao;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.RoleVO;
import com.serotonin.m2m2.vo.dataPoint.MockPointLocatorVO;
import com.serotonin.m2m2.vo.dataSource.mock.MockDataSourceVO;
import com.serotonin.m2m2.vo.permission.PermissionHolder;

/**
 * @author Terry Packer
 *
 */
public class RoleServiceTest extends ServiceTestBase<RoleVO, RoleDao, RoleService> {

    /**
     * Test the mapping table
     */
    @Test
    public void mappingTableWorks() {
        runTest(() -> {
            //First add a role to the edit permission of a point
            //Create data source?
            MockDataSourceVO ds = new MockDataSourceVO();
            ds.setXid("DS_TEST1");
            ds.setName("TEST");
            DataSourceDao.getInstance().save(ds);
            
            DataPointVO dp = new DataPointVO();
            dp.setXid("DP_PERM_TEST");
            dp.setPointLocator(new MockPointLocatorVO(DataTypes.NUMERIC, true));
            dp.setDataSourceId(ds.getId());
            dp.setReadPermission("read-role");
            DataPointDao.getInstance().save(dp);
            
            //TODO Wire into data point service?
            //Mock up the insert into the mapping table for now
            RoleService roleService = Common.getBean(RoleService.class);
            roleService.addRoleToVoPermission(readRole, dp, PermissionService.READ, PermissionHolder.SYSTEM_SUPERADMIN);
            roleService.addRoleToVoPermission(editRole, dp, PermissionService.EDIT, PermissionHolder.SYSTEM_SUPERADMIN);
            
            PermissionService service = Common.getBean(PermissionService.class);

            assertTrue(service.hasPermission(readUser, dp, PermissionService.READ));
            assertTrue(service.hasPermission(editUser, dp, PermissionService.EDIT));

            assertFalse(service.hasPermission(readUser, dp, PermissionService.SET));
            assertFalse(service.hasPermission(setUser, dp, PermissionService.SET));
        });
    }

    @Test(expected = ValidationException.class)
    public void cannotInsertNewUserRole() {
        RoleVO vo = newVO();
        vo.setXid(RoleDao.USER_ROLE_NAME);
        vo.setName("User default");
        service.insertFull(vo, systemSuperadmin);
    }

    @Test(expected = ValidationException.class)
    public void cannotInsertSuperadminRole() {
        RoleVO vo = newVO();
        vo.setXid(RoleDao.SUPERADMIN_ROLE_NAME);
        vo.setName("Superadmin default");
        service.insertFull(vo, systemSuperadmin);
    }
    
    @Test(expected = ValidationException.class)
    public void cannotModifyUserRole() {
        RoleVO vo = service.getFull(RoleDao.USER_ROLE_NAME, systemSuperadmin);
        vo.setName("User default changed");
        service.updateFull(vo.getXid(), vo, systemSuperadmin);
    }

    @Test(expected = ValidationException.class)
    public void cannotModifySuperadminRole() {
        RoleVO vo = service.getFull(RoleDao.SUPERADMIN_ROLE_NAME, systemSuperadmin);
        vo.setName("Superadmin default changed");
        service.updateFull(vo.getXid(), vo, systemSuperadmin);
    }
    
    @Override
    RoleService getService() {
        return Common.getBean(RoleService.class);
    }
    
    @Override
    RoleDao getDao() {
        return RoleDao.getInstance();
    }

    @Override
    void assertVoEqual(RoleVO expected, RoleVO actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getXid(), actual.getXid());
        assertEquals(expected.getName(), actual.getName());
    }

    @Override
    RoleVO newVO() {
        RoleVO vo = new RoleVO();
        vo.setXid(dao.generateUniqueXid());
        vo.setName("default test role");
        return vo;
    }

    @Override
    RoleVO updateVO(RoleVO existing) {
        RoleVO copy = existing.copy();
        copy.setName("updated");
        copy.setXid("NEW_XID");
        return copy;
    }
}