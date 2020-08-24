/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.livedata.internal.rest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataPropertyDescriptor;
import org.xwiki.livedata.LiveDataSource;
import org.xwiki.livedata.rest.LiveDataPropertyTypesResource;
import org.xwiki.livedata.rest.LiveDataSourceResource;
import org.xwiki.livedata.rest.model.jaxb.PropertyDescriptor;
import org.xwiki.livedata.rest.model.jaxb.StringMap;
import org.xwiki.livedata.rest.model.jaxb.Types;
import org.xwiki.rest.Relations;
import org.xwiki.rest.model.jaxb.Link;

/**
 * Default implementation of {@link LiveDataPropertyTypesResource}.
 * 
 * @version $Id$
 * @since 12.6
 */
@Component
@Named("org.xwiki.livedata.internal.rest.DefaultLiveDataPropertyTypesResource")
@Singleton
public class DefaultLiveDataPropertyTypesResource extends AbstractLiveDataResource
    implements LiveDataPropertyTypesResource
{
    @Override
    public Types getTypes(String hint, StringMap sourceParams, String namespace) throws Exception
    {
        Optional<LiveDataSource> source = getLiveDataSource(hint, sourceParams, namespace);
        if (source.isPresent()) {
            return createPropertyTypes(source.get().getPropertyTypes().get(), hint, namespace);
        } else {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
    }

    private Types createPropertyTypes(Collection<LiveDataPropertyDescriptor> propertyTypes, String hint,
        String namespace)
    {
        Link self = new Link().withRel(Relations.SELF).withHref(this.uriInfo.getAbsolutePath().toString());
        Link parent = withNamespace(createLink(Relations.PARENT, LiveDataSourceResource.class, hint), namespace);

        List<PropertyDescriptor> types = propertyTypes.stream()
            .map(propertyType -> createPropertyType(propertyType, hint, namespace)).collect(Collectors.toList());
        return (Types) new Types().withTypes(types).withLinks(self, parent);
    }
}
