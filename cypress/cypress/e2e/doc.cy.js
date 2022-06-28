describe('doc page',()=>{
    it('asks for a doc', ()=>{
        cy.visit('http://localhost:3000/doc');
        cy.get('input#doc').type(':hello');
        cy.get('button').contains('Go').click();
        cy.location().should((loc)=>{
            expect(loc.pathname).to.eq('/doc/%3Ahello');
        });
    });

    it('doc shows attributes',()=>{
        cy.visit('http://localhost:3000/doc/%3Ahello');
        cy.get('td').contains(':greeting');
        cy.get('td').contains('"hello XTDB inspector world!"');
    });

    it('is possible to add attribute',()=>{
        cy.visit('http://localhost:3000/doc/%3Ahello');
        cy.get('input[placeholder="New attr kw"]').type(':test-attr');
        cy.get('select').select('EDN');
        cy.get('input[placeholder="EDN"]').type('"hello cypress"').blur();
        cy.get('td').contains(':test-attr');
        cy.get('td').contains('"hello cypress"');
    });

});
