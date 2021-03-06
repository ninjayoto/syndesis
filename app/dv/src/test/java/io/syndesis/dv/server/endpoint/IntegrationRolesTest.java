/*
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.syndesis.dv.server.endpoint;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import io.syndesis.dv.model.TablePrivileges;
import io.syndesis.dv.model.TablePrivileges.Privilege;
import io.syndesis.dv.model.ViewDefinition;
import io.syndesis.dv.server.Application;
import io.syndesis.dv.server.endpoint.IntegrationTest.IntegrationTestConfiguration;
import io.syndesis.dv.server.endpoint.RoleInfo.Operation;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = {IntegrationTestConfiguration.class, Application.class})
@SuppressWarnings("nls")
public class IntegrationRolesTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void testCreateUpdate() throws Exception {
        RestDataVirtualization rdv = new RestDataVirtualization();
        String dvName = "testRoles";
        rdv.setName(dvName);
        rdv.setDescription("description");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/v1/virtualizations", rdv, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ViewDefinition vd = new ViewDefinition(dvName, "myview");
        vd.setComplete(true);
        vd.setDdl("create view myview as select 1");
        vd.setUserDefined(true);

        ResponseEntity<RestViewDefinitionStatus> stashStatus = restTemplate.exchange(
                "/v1/editors", HttpMethod.PUT,
                new HttpEntity<ViewDefinition>(vd), RestViewDefinitionStatus.class);

        assertEquals(HttpStatus.OK, stashStatus.getStatusCode());

        //no roles yet, but there's any authenticated
        ResponseEntity<List> rolesResponse = restTemplate.getForEntity("/v1/status/roles", List.class);
        assertEquals(HttpStatus.OK, rolesResponse.getStatusCode());
        assertEquals("[any authenticated]", rolesResponse.getBody().toString());

        ResponseEntity<RoleInfo> statusResponse = restTemplate.getForEntity("/v1/virtualizations/{dv}/roles", RoleInfo.class, dvName);
        assertEquals(HttpStatus.OK, statusResponse.getStatusCode());
        assertEquals("[]", statusResponse.getBody().getTablePrivileges().toString());

        RoleInfo toGrant = new RoleInfo();
        toGrant.getTablePrivileges().add(new TablePrivileges("x", stashStatus.getBody().getViewDefinition().getId(), Privilege.S));

        //grant a privilege
        ResponseEntity<String> grant = restTemplate.exchange(
                "/v1/virtualizations/{dv}/roles", HttpMethod.PUT,
                new HttpEntity<RoleInfo>(toGrant), String.class, dvName);
        assertEquals(HttpStatus.OK, grant.getStatusCode());

        //check that it got added
        rolesResponse = restTemplate.getForEntity("/v1/status/roles", List.class);
        assertEquals(HttpStatus.OK, rolesResponse.getStatusCode());
        assertEquals("[x, any authenticated]", rolesResponse.getBody().toString());

        statusResponse = restTemplate.getForEntity("/v1/virtualizations/{dv}/roles", RoleInfo.class, dvName);
        assertEquals(HttpStatus.OK, statusResponse.getStatusCode());
        assertEquals("[SELECT]", statusResponse.getBody().getTablePrivileges().get(0).getGrantPrivileges().toString());

        //add delete
        toGrant.getTablePrivileges().get(0).setGrantPrivileges(Collections.singleton(Privilege.D));

        grant = restTemplate.exchange(
                "/v1/virtualizations/{dv}/roles", HttpMethod.PUT,
                new HttpEntity<RoleInfo>(toGrant), String.class, dvName);
        assertEquals(HttpStatus.OK, grant.getStatusCode());

        //check that it got added
        statusResponse = restTemplate.getForEntity("/v1/virtualizations/{dv}/roles", RoleInfo.class, dvName);
        assertEquals(HttpStatus.OK, statusResponse.getStatusCode());
        assertEquals(new TreeSet<>(Arrays.asList(Privilege.S, Privilege.D)), statusResponse.getBody().getTablePrivileges().get(0).getGrantPrivileges());

        toGrant.setOperation(Operation.REVOKE);

        //let's back that out
        grant = restTemplate.exchange(
                "/v1/virtualizations/{dv}/roles", HttpMethod.PUT,
                new HttpEntity<RoleInfo>(toGrant), String.class, dvName);
        assertEquals(HttpStatus.OK, grant.getStatusCode());

        //check that it got removed
        statusResponse = restTemplate.getForEntity("/v1/virtualizations/{dv}/roles", RoleInfo.class, dvName);
        assertEquals(HttpStatus.OK, statusResponse.getStatusCode());
        assertEquals("[SELECT]", statusResponse.getBody().getTablePrivileges().get(0).getGrantPrivileges().toString());
    }

    @Test public void testStatus() {
        ResponseEntity<StatusObject> statusResponse = restTemplate.getForEntity("/v1/status", StatusObject.class);
        assertEquals(HttpStatus.OK, statusResponse.getStatusCode());
        assertEquals("{exposeVia3scale=false, ssoConfigured=false}", statusResponse.getBody().getAttributes().toString());
    }

}
