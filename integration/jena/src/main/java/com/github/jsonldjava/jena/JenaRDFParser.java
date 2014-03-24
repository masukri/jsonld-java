package com.github.jsonldjava.jena;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdError.Error;
import com.github.jsonldjava.core.RDFDataset;
import com.hp.hpl.jena.graph.Graph;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.sparql.core.DatasetGraph;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;

public class JenaRDFParser implements com.github.jsonldjava.core.RDFParser {

    // name generator
    protected Iterator<String> _ng = new Iterator<String>() {
        final AtomicInteger i = new AtomicInteger(0);

        @Override
        public void remove() {
            // Do nothing for remove
        }

        @Override
        public String next() {
            return "_:t" + i.incrementAndGet();
        }

        @Override
        public boolean hasNext() {
            return true;
        }
    };
    protected Map<String, String> _bns = new LinkedHashMap<String, String>();

    protected String getNameForBlankNode(String node) {
        if (!_bns.containsKey(node)) {
            _bns.put(node, _ng.next());
        }
        return _bns.get(node);
    }

    public void setPrefix(String fullUri, String prefix) {
        // TODO: graphs?
        // _context.put(prefix, fullUri);
    }

    public String getID(Node r) {
        String rval = null;
        if (r.isBlank()) {
            rval = getNameForBlankNode(r.getBlankNodeLabel());
        } else {
            rval = r.getURI();
        }
        return rval;
    }

    public String getID(Resource r) {
        String rval = null;
        if (r.isAnon()) {
            rval = getNameForBlankNode(r.getId().toString());
        } else {
            rval = r.getURI();
        }
        return rval;
    }

    public void importModel(RDFDataset result, Model model) {

        // Map the contexts from the Model to the RDFDataset
        final Map<String, String> nsPrefixMap = model.getNsPrefixMap();
        for (final String prefix : nsPrefixMap.keySet()) {
            result.setNamespace(prefix, nsPrefixMap.get(prefix));
        }

        // iterate over the list of subjects and add the edges to the json-ld
        // document
        final ResIterator subjects = model.listSubjects();
        while (subjects.hasNext()) {
            final Resource subject = subjects.next();
            importResource(result, subject);
        }
    }

    public void importResource(RDFDataset result, Resource subject) {
        final String subj = getID(subject);
        final StmtIterator statements = subject.getModel().listStatements(subject, (Property) null,
                (RDFNode) null);
        while (statements.hasNext()) {
            final Statement statement = statements.next();
            final Property predicate = statement.getPredicate();
            final RDFNode object = statement.getObject();

            if (object.isLiteral()) {
                final Literal literal = object.asLiteral();
                final String value = literal.getLexicalForm();
                final String datatypeURI = literal.getDatatypeURI();
                String language = literal.getLanguage();
                if ("".equals(language)) {
                    language = null;
                }

                result.addTriple(subj, predicate.getURI(), value, datatypeURI, language);
            } else {
                final Resource resource = object.asResource();
                final String res = getID(resource);

                result.addTriple(subj, predicate.getURI(), res);
            }
        }
    }

    private void importGraph(RDFDataset result, Graph graph, String graphName) {
        final ExtendedIterator<Triple> triples = graph.find(null, null, null);
        while (triples.hasNext()) {
            final Triple t = triples.next();
            final String subj = getID(t.getSubject());
            final String prop = t.getPredicate().getURI();
            if (t.getObject().isLiteral()) {
                final String value = t.getObject().getLiteralLexicalForm();
                final String datatypeURI = t.getObject().getLiteralDatatypeURI();
                String language = t.getObject().getLiteralLanguage();
                if ("".equals(language)) {
                    language = null;
                }
                result.addQuad(subj, prop, value, datatypeURI, language, graphName);
            } else {
                result.addQuad(subj, prop, getID(t.getObject()), graphName);
            }
        }
    }

    private void importDatasetGraph(RDFDataset result, DatasetGraph input) {

        importGraph(result, input.getDefaultGraph(), "@default");

        final Iterator<Node> graphNodes = input.listGraphNodes();
        while (graphNodes.hasNext()) {
            final Node n = graphNodes.next();
            final Graph graph = input.getGraph(n);
            final String graphName = n.getURI();

            importGraph(result, graph, graphName);
        }
    }

    @Override
    public RDFDataset parse(Object input) throws JsonLdError {
        final RDFDataset result = new RDFDataset();
        // allow null input so we can use importModel and importResource before
        // calling fromRDF
        if (input == null) {
            return result;
        }
        if (input instanceof DatasetGraph) {
            importDatasetGraph(result, (DatasetGraph) input);
        } else if (input instanceof Resource) {
            importResource(result, (Resource) input);
        } else if (input instanceof Model) {
            importModel(result, (Model) input);
        } else {
            throw new JsonLdError(Error.INVALID_INPUT,
                    "Jena Serializer expects Model or resource input");
        }
        return result;
    }
}
