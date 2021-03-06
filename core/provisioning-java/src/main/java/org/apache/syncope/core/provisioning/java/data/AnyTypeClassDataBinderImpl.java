/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.core.provisioning.java.data;

import java.util.Collections;
import java.util.stream.Collectors;
import org.apache.syncope.common.lib.to.AnyTypeClassTO;
import org.apache.syncope.core.persistence.api.dao.AnyTypeDAO;
import org.apache.syncope.core.persistence.api.dao.DerSchemaDAO;
import org.apache.syncope.core.persistence.api.dao.PlainSchemaDAO;
import org.apache.syncope.core.persistence.api.dao.VirSchemaDAO;
import org.apache.syncope.core.persistence.api.entity.EntityFactory;
import org.apache.syncope.core.persistence.api.entity.AnyTypeClass;
import org.apache.syncope.core.persistence.api.entity.DerSchema;
import org.apache.syncope.core.persistence.api.entity.Entity;
import org.apache.syncope.core.persistence.api.entity.PlainSchema;
import org.apache.syncope.core.persistence.api.entity.VirSchema;
import org.apache.syncope.core.provisioning.api.data.AnyTypeClassDataBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AnyTypeClassDataBinderImpl implements AnyTypeClassDataBinder {

    private static final Logger LOG = LoggerFactory.getLogger(AnyTypeClassDataBinder.class);

    @Autowired
    private PlainSchemaDAO plainSchemaDAO;

    @Autowired
    private DerSchemaDAO derSchemaDAO;

    @Autowired
    private VirSchemaDAO virSchemaDAO;

    @Autowired
    private AnyTypeDAO anyTypeDAO;

    @Autowired
    private EntityFactory entityFactory;

    @Override
    public AnyTypeClass create(final AnyTypeClassTO anyTypeClassTO) {
        AnyTypeClass anyTypeClass = entityFactory.newEntity(AnyTypeClass.class);
        update(anyTypeClass, anyTypeClassTO);
        return anyTypeClass;
    }

    @Override
    public void update(final AnyTypeClass anyTypeClass, final AnyTypeClassTO anyTypeClassTO) {
        if (anyTypeClass.getKey() == null) {
            anyTypeClass.setKey(anyTypeClassTO.getKey());
        }

        plainSchemaDAO.findByAnyTypeClasses(Collections.singletonList(anyTypeClass)).forEach(schema -> {
            schema.setAnyTypeClass(null);
        });

        anyTypeClass.getPlainSchemas().clear();
        anyTypeClassTO.getPlainSchemas().forEach(schemaName -> {
            PlainSchema schema = plainSchemaDAO.find(schemaName);
            if (schema == null || schema.getAnyTypeClass() != null) {
                LOG.debug("Invalid or already in use" + PlainSchema.class.getSimpleName()
                        + "{}, ignoring...", schemaName);
            } else {
                anyTypeClass.add(schema);
            }
        });

        derSchemaDAO.findByAnyTypeClasses(Collections.singletonList(anyTypeClass)).forEach((schema) -> {
            schema.setAnyTypeClass(null);
        });

        anyTypeClass.getDerSchemas().clear();
        anyTypeClassTO.getDerSchemas().forEach(schemaName -> {
            DerSchema schema = derSchemaDAO.find(schemaName);
            if (schema == null || schema.getAnyTypeClass() != null) {
                LOG.debug("Invalid or already in use" + DerSchema.class.getSimpleName()
                        + "{}, ignoring...", schemaName);
            } else {
                anyTypeClass.add(schema);
            }
        });

        virSchemaDAO.findByAnyTypeClasses(Collections.singletonList(anyTypeClass)).forEach(schema -> {
            schema.setAnyTypeClass(null);
        });

        anyTypeClass.getVirSchemas().clear();
        anyTypeClassTO.getVirSchemas().forEach(schemaName -> {
            VirSchema schema = virSchemaDAO.find(schemaName);
            if (schema == null || schema.getAnyTypeClass() != null) {
                LOG.debug("Invalid or already in use" + VirSchema.class.getSimpleName()
                        + "{}, ignoring...", schemaName);
            } else {
                anyTypeClass.add(schema);
            }
        });
    }

    @Override
    public AnyTypeClassTO getAnyTypeClassTO(final AnyTypeClass anyTypeClass) {
        AnyTypeClassTO anyTypeClassTO = new AnyTypeClassTO();

        anyTypeClassTO.setKey(anyTypeClass.getKey());

        anyTypeClassTO.getInUseByTypes().addAll(
                anyTypeDAO.findByTypeClass(anyTypeClass).stream().map(Entity::getKey).collect(Collectors.toList()));

        anyTypeClassTO.getPlainSchemas().addAll(
                anyTypeClass.getPlainSchemas().stream().map(Entity::getKey).collect(Collectors.toList()));
        anyTypeClassTO.getDerSchemas().addAll(
                anyTypeClass.getDerSchemas().stream().map(Entity::getKey).collect(Collectors.toList()));
        anyTypeClassTO.getVirSchemas().addAll(
                anyTypeClass.getVirSchemas().stream().map(Entity::getKey).collect(Collectors.toList()));

        return anyTypeClassTO;
    }

}
