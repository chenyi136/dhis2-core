package org.hisp.dhis.startup;

/*
 * Copyright (c) 2004-2017, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hisp.dhis.chart.Chart;
import org.hisp.dhis.common.AnalyticalObject;
import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.eventchart.EventChart;
import org.hisp.dhis.eventreport.EventReport;
import org.hisp.dhis.organisationunit.OrganisationUnitGroup;
import org.hisp.dhis.organisationunit.OrganisationUnitGroupSet;
import org.hisp.dhis.organisationunit.OrganisationUnitGroupSetDimension;
import org.hisp.dhis.reporttable.ReportTable;
import org.hisp.dhis.system.startup.TransactionContextStartupRoutine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

public class AnalyticalObjectGroupSetDimensionUpgrader
    extends TransactionContextStartupRoutine
{
    private static final Log log = LogFactory.getLog( AnalyticalObjectGroupSetDimensionUpgrader.class );
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private IdentifiableObjectManager idObjectManager;


    @Override
    public void executeInTransaction()
    {
        try
        {
            upgradeOrgUnitGrupSetDimensions( "reporttable", ReportTable.class );
            upgradeOrgUnitGrupSetDimensions( "chart", Chart.class );
            upgradeOrgUnitGrupSetDimensions( "eventreport", EventReport.class );
            upgradeOrgUnitGrupSetDimensions( "eventchart", EventChart.class );
        }
        catch ( Exception ex )
        {
            log.info( "Error during group set dimensions upgrade of favorites, probably beacuse upgrade is done", ex );
            return;
        }
        
    }
    
    /**
     * @param analyticalObject the analytical object table name.
     * @param clazz the analytical object class.
     */
    private void upgradeOrgUnitGrupSetDimensions( String analyticalObject, Class<? extends AnalyticalObject> clazz )
    {
        String groupSetSqlFormat = 
            "select distinct d.%sid, gsm.orgunitgroupsetid " +
            "from %s_orgunitgroups d " +
            "inner join orgunitgroupsetmembers gsm on d.orgunitgroupid=gsm.orgunitgroupid";

        String groupSetSql = String.format( groupSetSqlFormat, analyticalObject, analyticalObject );
        
        String groupSqlFormat =
            "select d.orgunitgroupid " +
            "from %s_orgunitgroups d " +
            "inner join orgunitgroupsetmembers gsm on d.orgunitgroupid=gsm.orgunitgroupid " +
            "where d.%sid=%d " +
            "and gsm.orgunitgroupsetid=%d " +
            "order by d.sort_order ";
        
        SqlRowSet groupSetRs = jdbcTemplate.queryForRowSet( groupSetSql );
        
        while ( groupSetRs.next() )
        {
            int aoId = groupSetRs.getInt( 1 );
            int gsId = groupSetRs.getInt( 2 );
            
            AnalyticalObject ao = idObjectManager.get( clazz, aoId );
            OrganisationUnitGroupSet groupSet = idObjectManager.get( OrganisationUnitGroupSet.class, gsId );
            
            String groupSql = String.format( groupSqlFormat, analyticalObject, analyticalObject, aoId, gsId );
            
            List<OrganisationUnitGroup> groups = new ArrayList<>();
            
            SqlRowSet groupRs = jdbcTemplate.queryForRowSet( groupSql );
            
            while ( groupRs.next() )
            {
                int gId = groupRs.getInt( 1 );
                
                OrganisationUnitGroup group = idObjectManager.get( OrganisationUnitGroup.class, gId );                
                groups.add( group );
            }
            
            OrganisationUnitGroupSetDimension dimension = new OrganisationUnitGroupSetDimension();
            dimension.setDimension( groupSet );
            dimension.setItems( groups );
            
            ao.addOrganisationUnitGroupSetDimension( dimension );
            
            idObjectManager.update( ao );
            
            log.info( String.format( "Added org unit group set dimension: %s with groups: %d for favorite: %s", groupSet.getUid(), groups.size(), ao.getUid() ) );
        }        
    }
}