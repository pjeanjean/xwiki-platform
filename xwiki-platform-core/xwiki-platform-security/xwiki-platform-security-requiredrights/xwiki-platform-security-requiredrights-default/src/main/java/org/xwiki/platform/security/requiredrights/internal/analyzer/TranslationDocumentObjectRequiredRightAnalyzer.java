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
package org.xwiki.platform.security.requiredrights.internal.analyzer;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.platform.security.requiredrights.RequiredRight;
import org.xwiki.platform.security.requiredrights.RequiredRightAnalysisResult;
import org.xwiki.platform.security.requiredrights.RequiredRightAnalyzer;
import org.xwiki.platform.security.requiredrights.RequiredRightsException;
import org.xwiki.platform.security.requiredrights.internal.provider.BlockSupplierProvider;

import com.xpn.xwiki.objects.BaseObject;

/**
 * Required right analyzer for instances of XWiki.TranslationDocumentClass.
 *
 * @version $Id$
 */
@Component(hints = { "XWiki.TranslationDocumentClass" })
@Singleton
public class TranslationDocumentObjectRequiredRightAnalyzer implements RequiredRightAnalyzer<BaseObject>
{
    @Inject
    @Named("translation")
    private BlockSupplierProvider<String> translationMessageSupplierProvider;

    @Inject
    private BlockSupplierProvider<BaseObject> xObjectDisplayerProvider;

    @Override
    public List<RequiredRightAnalysisResult> analyze(BaseObject object) throws RequiredRightsException
    {
        String scope = object.getStringValue("scope");

        RequiredRight requiredRight;
        String translationKey;
        switch (scope) {
            case "GLOBAL":
                requiredRight = RequiredRight.PROGRAM;
                translationKey = "security.requiredrights.object.translationDocument.global";
                break;

            case "WIKI":
                requiredRight = RequiredRight.ADMIN;
                translationKey = "security.requiredrights.object.translationDocument.wiki";
                break;

            case "USER":
                requiredRight = RequiredRight.SCRIPT;
                translationKey = "security.requiredrights.object.translationDocument.user";
                break;

            default:
                return List.of();
        }

        return List.of(new RequiredRightAnalysisResult(object.getReference(),
            this.translationMessageSupplierProvider.get(translationKey), this.xObjectDisplayerProvider.get(object),
            List.of(requiredRight)));
    }
}
