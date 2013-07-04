/** 
 
Copyright 2013 Intel Corporation, All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License. 
*/ 

package com.intel.cosbench.driver.service;

import static org.apache.commons.lang.SystemUtils.IS_OS_WINDOWS;

import java.io.File;

import com.intel.cosbench.api.auth.AuthAPIService;
import com.intel.cosbench.api.ioengine.IOEngineAPIService;
import com.intel.cosbench.api.storage.StorageAPIService;
import com.intel.cosbench.config.castor.CastorConfigTools;
import com.intel.cosbench.driver.model.DriverContext;
import com.intel.cosbench.service.*;

public class COSBDriverServiceFactory extends AbstractServiceFactory implements
        DriverServiceFactory {

    private static final String SERVICE_NAME = "driver";

    private static final String CFG_FILE_KEY = "cosbench.driver.config";

    private static final String UNIX_DEFAULT_CFG_FILE = "/etc/cosbench/driver.conf";

    private static final String WIN_DEFAULT_CFG_FILE = "C:\\driver.conf";

    private AuthAPIService authAPIs;
    private StorageAPIService storageAPIs;
    private IOEngineAPIService ioengineAPIs;

    public COSBDriverServiceFactory() {
        /* force a XML mapping loading */
        CastorConfigTools.getMissionResolver();
    }

    public void setAuthAPIs(AuthAPIService apis) {
        this.authAPIs = apis;
    }

    public void setStorageAPIs(StorageAPIService apis) {
        this.storageAPIs = apis;
    }
    
    public void setIoengineAPIs(IOEngineAPIService apis) {
        this.ioengineAPIs = apis;
    }

    @Override
    protected String getConfigFile() {
        String configFile;
        if ((configFile = System.getProperty(CFG_FILE_KEY)) != null)
            return configFile;
        if (new File("driver.conf").exists())
            return "driver.conf";
        if (new File("conf/driver.conf").exists())
            return "conf/driver.conf";
        return IS_OS_WINDOWS ? WIN_DEFAULT_CFG_FILE : UNIX_DEFAULT_CFG_FILE;
    }

    @Override
    protected String getServiceName() {
        return SERVICE_NAME;
    }

    @Override
    public DriverService getDriverService() {
        COSBDriverService service = new COSBDriverService();
        DriverContext context = getDriverContext();
        service.setContext(context);
        service.setIoengineAPIs(ioengineAPIs);
        service.setAuthAPIs(authAPIs);
        service.setStorageAPIs(storageAPIs);
        service.init();
        return service;
    }

    private DriverContext getDriverContext() {
        DriverContext context = new DriverContext();
        context.setName(loadDriverName());
        context.setUrl(loadDriverUrl());
        return context;
    }

    protected String loadLogLevel() {
        return config.get("driver.log_level", "INFO");
    }

    protected String loadLogFile() {
        return config.get("driver.log_file", "log/system.log");
    }

    private String loadDriverName() {
        return config.get("driver.name", "N/A");
    }

    private String loadDriverUrl() {
        return config.get("driver.url", "N/A");
    }

}
