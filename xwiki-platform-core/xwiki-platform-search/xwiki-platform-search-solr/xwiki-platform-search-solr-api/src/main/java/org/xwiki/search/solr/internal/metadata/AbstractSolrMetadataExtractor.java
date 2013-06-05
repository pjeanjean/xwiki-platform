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
package org.xwiki.search.solr.internal.metadata;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.context.Execution;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.search.solr.internal.api.Fields;
import org.xwiki.search.solr.internal.api.SolrIndexerException;
import org.xwiki.search.solr.internal.reference.SolrReferenceResolver;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.BaseProperty;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.PasswordClass;
import com.xpn.xwiki.objects.classes.PropertyClass;

/**
 * Abstract implementation for a metadata extractor.
 * 
 * @version $Id$
 * @since 4.3M2
 */
public abstract class AbstractSolrMetadataExtractor implements SolrMetadataExtractor
{
    /**
     * The format used when indexing the objcontent field: "&lt;propertyName&gt;:&lt;propertyValue&gt;".
     */
    private static final String OBJCONTENT_FORMAT = "%s:%s";

    /**
     * Logging framework.
     */
    @Inject
    protected Logger logger;

    /**
     * Execution component.
     */
    @Inject
    protected Execution execution;

    /**
     * Reference to String serializer. Used for fields such as class and fullname that are relative to their wiki and
     * are stored without the wiki name.
     */
    @Inject
    @Named("local")
    protected EntityReferenceSerializer<String> localSerializer;

    /**
     * DocumentAccessBridge component.
     */
    @Inject
    protected DocumentAccessBridge documentAccessBridge;

    /**
     * Used to access current {@link XWikiContext}.
     */
    @Inject
    protected Provider<XWikiContext> xcontextProvider;

    /**
     * Used to find the resolver.
     */
    @Inject
    protected ComponentManager componentManager;

    @Override
    public LengthSolrInputDocument getSolrDocument(EntityReference entityReference) throws SolrIndexerException,
        IllegalArgumentException
    {
        DocumentReference documentReference =
            new DocumentReference(entityReference.extractReference(EntityType.DOCUMENT));

        try {
            LengthSolrInputDocument solrDocument = new LengthSolrInputDocument();

            solrDocument.addField(Fields.ID, getResolver(entityReference).getId(documentReference));

            addDocumentFields(documentReference, solrDocument);

            solrDocument.addField(Fields.TYPE, documentReference.getType().name());

            addFieldsInternal(solrDocument, entityReference);

            return solrDocument;
        } catch (Exception e) {
            throw new SolrIndexerException(String.format("Failed to get input document for '%s'", documentReference), e);
        }
    }

    /**
     * @param solrDocument the {@link LengthSolrInputDocument} to modify
     * @param entityReference the reference of the entity
     * @throws Exception in case of errors
     */
    protected abstract void addFieldsInternal(LengthSolrInputDocument solrDocument, EntityReference entityReference)
        throws Exception;

    /**
     * @param entityReference the reference of the entity
     * @return the Solr resolver associated to the entity type
     * @throws SolrIndexerException if any error
     */
    protected SolrReferenceResolver getResolver(EntityReference entityReference) throws SolrIndexerException
    {
        try {
            return componentManager.getInstance(SolrReferenceResolver.class, entityReference.getType().toString()
                .toLowerCase());
        } catch (ComponentLookupException e) {
            throw new SolrIndexerException("Faile to find solr reference redolver for type reference ["
                + entityReference + "]");
        }
    }

    /**
     * Utility method.
     * 
     * @param documentReference reference to a document.
     * @return the {@link XWikiDocument} instance referenced.
     * @throws XWikiException if problems occur.
     */
    protected XWikiDocument getDocument(DocumentReference documentReference) throws XWikiException
    {
        XWikiContext xcontext = this.xcontextProvider.get();

        XWikiDocument document = xcontext.getWiki().getDocument(documentReference, xcontext);

        return document;
    }

    /**
     * Fetch translated document.
     * 
     * @param documentReference reference to the document to be translated.
     * @return translated document.
     * @throws SolrIndexerException if problems occur.
     */
    protected XWikiDocument getTranslatedDocument(DocumentReference documentReference) throws SolrIndexerException
    {
        try {
            XWikiDocument document = getDocument(documentReference);
            XWikiDocument translatedDocument =
                document.getTranslatedDocument(documentReference.getLocale(), this.xcontextProvider.get());
            return translatedDocument;
        } catch (Exception e) {
            throw new SolrIndexerException(String.format("Failed to get translated document for '%s'",
                documentReference), e);
        }
    }

    /**
     * Adds to a Solr document the fields that are specific to the XWiki document that contains the entity to be
     * indexed. These fields required to identify the owning document and to also reflect some properties of the owning
     * document towards the indexed entity (like locale and hidden flag).
     * 
     * @param documentReference reference to document.
     * @param solrDocument the Solr document to which to add the fields.
     * @throws Exception if problems occur.
     */
    protected void addDocumentFields(DocumentReference documentReference, SolrInputDocument solrDocument)
        throws Exception
    {
        solrDocument.addField(Fields.WIKI, documentReference.getWikiReference().getName());
        solrDocument.addField(Fields.SPACE, documentReference.getLastSpaceReference().getName());
        solrDocument.addField(Fields.NAME, documentReference.getName());

        String locale = getLocale(documentReference);
        solrDocument.addField(Fields.LOCALE, locale);

        XWikiDocument document = getDocument(documentReference);
        solrDocument.addField(Fields.HIDDEN, document.isHidden());
    }

    /**
     * @param documentReference reference to the document.
     * @return the locale code of the referenced document.
     * @throws SolrIndexerException if problems occur.
     */
    protected String getLocale(DocumentReference documentReference) throws SolrIndexerException
    {
        String locale = null;

        try {
            if (documentReference.getLocale() != null && !StringUtils.isEmpty(documentReference.getLocale().toString())) {
                locale = documentReference.getLocale().toString();
            } else if (StringUtils.isNotEmpty(this.documentAccessBridge.getDocument(documentReference)
                .getRealLanguage())) {
                locale = this.documentAccessBridge.getDocument(documentReference).getRealLanguage();
            } else {
                // Multilingual and Default placeholder
                locale = "en";
            }
        } catch (Exception e) {
            throw new SolrIndexerException(String.format("Exception while fetching the locale of the document '%s'",
                documentReference), e);
        }

        return locale;
    }

    /**
     * Adds the properties of a given object to a Solr document inside the multiValued field
     * {@link Fields#OBJECT_CONTENT}.
     * 
     * @param solrDocument the document where to add the properties.
     * @param object the object whose properties to add.
     * @param locale the locale of the indexed document. In case of translations, this will obviously be different than
     *            the original document's locale.
     */
    protected void addObjectContent(SolrInputDocument solrDocument, BaseObject object, String locale)
    {
        if (object == null) {
            // Yes, the platform can return null objects.
            return;
        }

        String fieldName = String.format(Fields.MULTILIGNUAL_FORMAT, Fields.OBJECT_CONTENT, locale);

        XWikiContext xcontext = this.xcontextProvider.get();

        BaseClass xClass = object.getXClass(xcontext);

        for (Object field : object.getFieldList()) {
            BaseProperty<EntityReference> property = (BaseProperty<EntityReference>) field;

            // Avoid indexing empty properties.
            Object propertyValue = property.getValue();
            if (propertyValue != null) {
                // Avoid indexing password.
                PropertyClass propertyClass = (PropertyClass) xClass.get(property.getName());
                if (propertyClass instanceof PasswordClass) {
                    continue;
                } else if (propertyValue instanceof List) {
                    // Handle list property values, by adding each list entry.
                    List< ? > propertyListValues = (List< ? >) propertyValue;
                    for (Object propertyListValue : propertyListValues) {
                        solrDocument.addField(fieldName,
                            String.format(OBJCONTENT_FORMAT, property.getName(), propertyListValue));
                    }
                } else {
                    // Generic toString on the property value
                    solrDocument.addField(fieldName,
                        String.format(OBJCONTENT_FORMAT, property.getName(), propertyValue));
                }
            }
        }
    }
}
