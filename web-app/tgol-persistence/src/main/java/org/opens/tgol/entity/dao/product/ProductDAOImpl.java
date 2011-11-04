/*
 * Tanaguru - Automated webpage assessment
 * Copyright (C) 2008-2011  Open-S Company
 *
 * This file is part of Tanaguru.
 *
 * Tanaguru is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact us by mail: open-s AT open-s DOT com
 */
package org.opens.tgol.entity.dao.product;

import org.opens.tanaguru.sdk.entity.dao.jpa.AbstractJPADAO;
import org.opens.tgol.entity.product.Product;
import org.opens.tgol.entity.product.ProductImpl;
import javax.persistence.Query;

/**
 *
 * @author jkowalczyk
 */
public class ProductDAOImpl extends AbstractJPADAO<Product, Long> implements ProductDAO {

    private static final String CACHEABLE_OPTION="org.hibernate.cacheable";

    public ProductDAOImpl(){
        super();
    }

    @Override
    protected Class<? extends Product> getEntityClass() {
        return ProductImpl.class;
    }

    @Override
    public Product read(Long key) {
        if (key == null) {
            return null;
        }
        Query query = entityManager.createQuery("SELECT p FROM "
                + getEntityClass().getName() + " p"
                + " left join fetch p.contractSet c"
                + " WHERE p.id = :id");
        query.setParameter("id", key);
        return (Product)query.getSingleResult();
    }

}