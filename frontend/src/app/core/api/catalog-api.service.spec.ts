import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CatalogApiService } from './catalog-api.service';

describe('CatalogApiService', () => {
  it('keeps the protected category list and creation inside the catalog API', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(CatalogApiService);
    const http = TestBed.inject(HttpTestingController);

    service.listCategories('alimento', 'ACTIVA').subscribe();
    const list = http.expectOne('/api/v1/catalog/categories?query=alimento&status=ACTIVA');
    expect(list.request.method).toBe('GET');
    list.flush({ data: [] });

    const category = { name: 'Alimentos', status: 'ACTIVA' as const };
    service.createCategory(category).subscribe();
    const create = http.expectOne('/api/v1/catalog/categories');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(category);
    create.flush({ data: { ...category, id: 'category-1', tenantId: 'tenant-1' } });
    http.verify();
  });

  it('sends product changes without a tenant identifier supplied by the browser', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(CatalogApiService);
    const http = TestBed.inject(HttpTestingController);
    const product = {
      categoryId: 'category-1', sku: 'ALM-001', title: 'Alimento sintético', description: null,
      salePricePen: 52.5, status: 'ACTIVO_NO_PUBLICADO' as const, inventoryManaged: true, imageUrl: null,
    };

    service.createProduct(product).subscribe();
    const create = http.expectOne('/api/v1/catalog/products');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(product);
    expect(create.request.body.tenantId).toBeUndefined();
    create.flush({ data: { ...product, id: 'product-1', tenantId: 'tenant-1' } });
    http.verify();
  });
});
