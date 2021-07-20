package org.hibernate.type;

import org.hibernate.annotations.SourceType;
import org.hibernate.type.descriptor.java.RowVersionTypeDescriptor;
import org.hibernate.type.descriptor.sql.VarbinaryTypeDescriptor;

public class SQLServerRowVersionType extends RowVersionType {

	public String getName() {
		return SourceType.DBBINARY.typeName();
	}

	public static final SQLServerRowVersionType INSTANCE = new SQLServerRowVersionType();

	public SQLServerRowVersionType() {
		super( VarbinaryTypeDescriptor.INSTANCE, RowVersionTypeDescriptor.INSTANCE );
	}

}
