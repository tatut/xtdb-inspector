describe('Lucene search',()=>{
    it('queries after typing',()=>{
        cy.visit('http://localhost:3000/doc');
        cy.get('input[name=search]').type('dev*');
        cy.get('table.lucene-results').find('tr').should('have.length',10);

        // clear the input and results will be gone
        cy.get('input[name=search]').clear();
        cy.get('table.lucene-results').should('not.exist');
    });

    it('links to document',()=>{
        cy.visit('http://localhost:3000/doc');
        cy.get('input[name=search]').type('nathanial');
        cy.wait(500);
        cy.get('table.lucene-results').find('tr').contains('{:person-id 1}');
        cy.get('table.lucene-results tr a').click();
        cy.location().should((loc)=>{
            expect(loc.pathname).to.eq('/doc/_%7B%3Aperson-id%201%7D');
        });
    })

});
