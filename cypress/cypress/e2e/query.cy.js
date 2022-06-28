describe('Query page', () => {
    it('simple query and results', () => {
        cy.visit('http://localhost:3000/query');
        cy.window().then(win => win.editor.setValue(''));
        cy.get('.CodeMirror textarea').type(`
{:find [e thing]
 :where [[e :things thing]]}`, {force: true});
        cy.get('button').contains('Run query').click();
        cy.get('td a').contains(':hello');
        cy.get('td a').contains('"thing1"');
        cy.get('td a').contains('"thing3"');
    });

    it('can save a query', () => {
        cy.visit('http://localhost:3000/query');
        cy.window().then(win => win.editor.setValue(''));
        let q = `{:find [e thing] :where [[e :things thing]]}`;
        cy.get('.CodeMirror textarea').type(q, {force: true, parseSpecialCharSequences:false});
        cy.get('#save-query-as').type('test-query');
        cy.get('button').contains('Save').click();
        cy.window().then(win => win.editor.setValue(''));
        cy.wait(500);
        cy.get('select').select('test-query');
        cy.get('.CodeMirror textarea').should('have.value', q);
    });
})
