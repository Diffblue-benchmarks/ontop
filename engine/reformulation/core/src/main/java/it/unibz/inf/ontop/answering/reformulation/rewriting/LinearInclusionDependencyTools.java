package it.unibz.inf.ontop.answering.reformulation.rewriting;

import com.google.inject.Inject;
import it.unibz.inf.ontop.datalog.DatalogFactory;
import it.unibz.inf.ontop.datalog.LinearInclusionDependencies;
import it.unibz.inf.ontop.model.atom.AtomFactory;
import it.unibz.inf.ontop.model.term.Function;
import it.unibz.inf.ontop.model.term.TermFactory;
import it.unibz.inf.ontop.model.term.Variable;
import it.unibz.inf.ontop.model.vocabulary.RDF;
import it.unibz.inf.ontop.spec.ontology.*;

public class LinearInclusionDependencyTools {

    private final AtomFactory atomFactory;
    private final TermFactory termFactory;
    private final DatalogFactory datalogFactory;

    @Inject
    private LinearInclusionDependencyTools(AtomFactory atomFactory, TermFactory termFactory,
                                           DatalogFactory datalogFactory) {
        this.atomFactory = atomFactory;
        this.termFactory = termFactory;
        this.datalogFactory = datalogFactory;
    }

    public LinearInclusionDependencies  getABoxDependencies(ClassifiedTBox reasoner, boolean full) {
        LinearInclusionDependencies dependencies = new LinearInclusionDependencies(datalogFactory);

        for (Equivalences<ObjectPropertyExpression> propNode : reasoner.objectPropertiesDAG()) {
            // super might be more efficient
            for (Equivalences<ObjectPropertyExpression> subpropNode : reasoner.objectPropertiesDAG().getSub(propNode)) {
                for (ObjectPropertyExpression subprop : subpropNode) {
                    if (subprop.isInverse())
                        continue;

                    Function body = translate(subprop);

                    for (ObjectPropertyExpression prop : propNode)  {
                        if (prop == subprop)
                            continue;

                        Function head = translate(prop);
                        dependencies.addRule(head, body);
                    }
                }
            }
        }
        for (Equivalences<DataPropertyExpression> propNode : reasoner.dataPropertiesDAG()) {
            // super might be more efficient
            for (Equivalences<DataPropertyExpression> subpropNode : reasoner.dataPropertiesDAG().getSub(propNode)) {
                for (DataPropertyExpression subprop : subpropNode) {

                    Function body = translate(subprop);

                    for (DataPropertyExpression prop : propNode)  {
                        if (prop == subprop)
                            continue;

                        Function head = translate(prop);
                        dependencies.addRule(head, body);
                    }
                }
            }
        }
        for (Equivalences<ClassExpression> classNode : reasoner.classesDAG()) {
            // super might be more efficient
            for (Equivalences<ClassExpression> subclassNode : reasoner.classesDAG().getSub(classNode)) {
                for (ClassExpression subclass : subclassNode) {

                    Function body = translate(subclass, variableYname);
                    //if (!(subclass instanceof OClass) && !(subclass instanceof PropertySomeRestriction))
                    if (body == null)
                        continue;

                    for (ClassExpression cla : classNode)  {
                        if (!(cla instanceof OClass) && !(!full && ((cla instanceof ObjectSomeValuesFrom) || (cla instanceof DataSomeValuesFrom))))
                            continue;

                        if (cla == subclass)
                            continue;

                        // use a different variable name in case the body has an existential as well
                        Function head = translate(cla, variableZname);
                        dependencies.addRule(head, body);
                    }
                }
            }
        }

        return dependencies;
    }

    private static final String variableXname = "x";
    private static final String variableYname = "y";
    private static final String variableZname = "z";

    private Function translate(ObjectPropertyExpression property) {
        final Variable varX = termFactory.getVariable(variableXname);
        final Variable varY = termFactory.getVariable(variableYname);

        Function propertyFunction = termFactory.getUriTemplate(termFactory.getConstantLiteral(property.getIRI().getIRIString()));
        if (property.isInverse())
            return atomFactory.getMutableTripleAtom(varY, propertyFunction, varX);
        else
            return atomFactory.getMutableTripleAtom(varX, propertyFunction, varY);
    }

    private Function translate(DataPropertyExpression property) {
        final Variable varX = termFactory.getVariable(variableXname);
        final Variable varY = termFactory.getVariable(variableYname);

        Function propertyFunction = termFactory.getUriTemplate(termFactory.getConstantLiteral(property.getIRI().getIRIString()));
        return atomFactory.getMutableTripleAtom(varX, propertyFunction, varY);
    }

    private Function translate(ClassExpression description, String existentialVariableName) {
        final Variable varX = termFactory.getVariable(variableXname);
        if (description instanceof OClass) {
            OClass klass = (OClass) description;
            Function classFunction = termFactory.getUriTemplate(termFactory.getConstantLiteral(klass.getIRI().getIRIString()));
            Function rdfTypeFunction = termFactory.getUriTemplate(termFactory.getConstantLiteral(RDF.TYPE.getIRIString()));
            return atomFactory.getMutableTripleAtom(varX, rdfTypeFunction, classFunction);
        }
        else if (description instanceof ObjectSomeValuesFrom) {
            final Variable varY = termFactory.getVariable(existentialVariableName);
            ObjectPropertyExpression property = ((ObjectSomeValuesFrom) description).getProperty();
            Function propertyFunction = termFactory.getUriTemplate(termFactory.getConstantLiteral(property.getIRI().getIRIString()));
            if (property.isInverse())
                return atomFactory.getMutableTripleAtom(varY, propertyFunction, varX);
            else
                return atomFactory.getMutableTripleAtom(varX, propertyFunction, varY);
        }
        else {
            assert (description instanceof DataSomeValuesFrom);
            final Variable varY = termFactory.getVariable(existentialVariableName);
            DataPropertyExpression property = ((DataSomeValuesFrom) description).getProperty();
            Function propertyFunction = termFactory.getUriTemplate(termFactory.getConstantLiteral(property.getIRI().getIRIString()));
            return atomFactory.getMutableTripleAtom(varX, propertyFunction, varY);
        }
    }
}