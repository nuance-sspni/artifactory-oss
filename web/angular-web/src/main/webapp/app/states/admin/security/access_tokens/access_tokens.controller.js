import CONFIG_MESSAGES from "../../../../constants/configuration_messages.constants";

export class AccessTokensController {
    constructor($scope, AccessTokensDao, uiGridConstants, commonGridColumns, JFrogGridFactory, JFrogModal) {

        this.gridOption = {};
        this.uiGridConstants = uiGridConstants;
        this.commonGridColumns = commonGridColumns;
        this.AccessTokensDao = AccessTokensDao;
        this.artifactoryGridFactory = JFrogGridFactory;
        this.$scope = $scope;
        this.modal = JFrogModal;
        this.noTokensMessage = CONFIG_MESSAGES.admin.security.accessTokens.noTokensMessage;
        this._createGrid();
        this._getData();
    }

    _getData() {
        // get all tokens
        this.AccessTokensDao.getTokens().$promise.then((tokens)=> {
            this.gridOption.setGridData(tokens);
        });
    }

    _createGrid() {
        this.gridOption = this.artifactoryGridFactory.getGridInstance(this.$scope)
                .setColumns(this.getColumns())
                .setButtons(this._getActions())
                .setMultiSelect()
                .setRowTemplate('default')
                .setBatchActions(this._getBatchActions());
    }


    getColumns() {
        return [
            {
                field: "subject",
                name: "Subject",
                displayName: "Subject",
                cellTemplate: '<div class="ui-grid-cell-contents">{{row.entity.subject}}</div>',
                width: '28%'
            },
            {
                field: 'tokenId',
                name: 'Token ID',
                displayName: 'Token ID',
                cellTemplate: '<div class="ui-grid-cell-contents">{{row.entity.tokenId}}</div>',
                sort: {
                    direction: this.uiGridConstants.ASC
                },
                width: '24%'
            },
            {
                field: "issuedAt",
                name: "Issued At",
                displayName: "Issued At",
                cellTemplate: '<div class="ui-grid-cell-contents">{{row.entity.issuedAt}}</div>',
                width: '18%'
            },
            {
                field: "expiryDate",
                name: "Expiry Date",
                displayName: "Expiry Date",
                cellTemplate: '<div class="ui-grid-cell-contents">{{row.entity.expiry}}</div>',
                width: '18%'
            },
            {
                field: "refreshable",
                name: "Refreshable",
                displayName: "Refreshable",
                cellTemplate: '<div class="ui-grid-cell-contents">{{row.entity.refreshable}}</div>',
                width: '12%'
            }
        ]
    }

    _getActions() {
        return [
            {
                icon: 'icon icon-clear',
                tooltip: 'Revoke',
                callback: (row) => this.revokeToken(row)
            }

        ];
    }
    _getBatchActions() {
        return [
            {
                icon: 'clear',
                name: 'Revoke',
                callback: () => this.bulkRevoke()
            }
        ]
    }

    revokeToken(token) {
        // Create array with single token ID to revoke
        let json = [token.tokenId];
        this.modal.confirm(`Are you sure you want to revoke this access token? Once revoked,
                            it can not be used again.`, 'Revoke access token', {confirm: 'Revoke'})
                .then(() => this.AccessTokensDao.revokeTokens(json).$promise.then(()=>this._getData()));
    }


    bulkRevoke() {
        // Get All selected users
        let selectedRows = this.gridOption.api.selection.getSelectedRows();
        // Create an array of the selected tokens
        let tokenIds = _.map(selectedRows, (token) => {return token.tokenId;});

        //Ask for confirmation before revoke and if confirmed then revoke bulk of tokens
        this.modal.confirm(`Are you sure you want to revoke these access tokens? Once revoked, 
                            they can not be used again.`, 'Revoke access tokens', {confirm: 'Revoke'})
                            .then(() => this.AccessTokensDao.revokeTokens(tokenIds).$promise.then(() => this._getData()));
    }

}